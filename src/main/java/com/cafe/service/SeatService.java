package com.cafe.service;

import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.TimeUtil;
import com.cafe.dto.SeatView;
import com.cafe.entity.Reservation;
import com.cafe.entity.Seat;
import com.cafe.entity.SeatSession;
import com.cafe.mapper.ReservationMapper;
import com.cafe.mapper.SeatMapper;
import com.cafe.mapper.SeatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机位服务 —— 负责机位状态机与状态流转。
 *
 * 状态取值（三态，不设已预约状态）：
 *     FREE        空闲，可开台
 *     USING       使用中，已开台
 *     MAINTENANCE 维护中，不可开台
 *
 * 为什么不设「已预约」状态：预约通过 reservation 表的时段重叠检测来控制。
 * 若预约成功就把机位标记为已预约并锁死，那么「预约今晚 8 点」会导致这台机器
 * 从现在起就无法使用，与实际经营不符。
 *
 * 状态流转合法性由本类统一校验，任何非法跳转（如维护中直接开台）都会被拒绝，
 * 而不是依赖调用方自觉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;
    private final SeatSessionMapper sessionMapper;
    private final ReservationMapper reservationMapper;

    /**
     * 合法流转表：key 为当前状态，value 为允许切换到的目标状态。
     * 使用常量而非散落的 if 判断，新增状态时只需改这一处。
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            Constants.SEAT_FREE, Set.of(Constants.SEAT_USING, Constants.SEAT_MAINTENANCE),
            Constants.SEAT_USING, Set.of(Constants.SEAT_FREE),
            Constants.SEAT_MAINTENANCE, Set.of(Constants.SEAT_FREE)
    );

    private static final Map<String, String> STATUS_NAME = Map.of(
            Constants.SEAT_FREE, "空闲",
            Constants.SEAT_USING, "使用中",
            Constants.SEAT_MAINTENANCE, "维护中"
    );

    /* ==================== 查询 ==================== */

    public List<Seat> listAll() {
        return seatMapper.selectList(null);
    }

    /** 查询空闲机位（开台时选择） */
    public List<Seat> listFree() {
        return seatMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Seat>()
                        .eq("status", Constants.SEAT_FREE)
                        .orderByAsc("seat_no"));
    }

    /** 按区域查询机位 */
    public List<Seat> listByArea(String area) {
        return seatMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Seat>()
                        .eq(area != null && !area.isBlank(), "area", area)
                        .orderByAsc("seat_no"));
    }

    public Seat lockById(Long seatId) {
        Seat seat = seatMapper.selectForUpdate(seatId);
        if (seat == null) throw new BizException("机位不存在，ID=" + seatId);
        return seat;
    }

    public Seat getById(Long seatId) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new BizException("机位不存在，ID=" + seatId);
        }
        return seat;
    }

    /** 各区域机位总数、空闲数、使用中数量，用于机位管理页概览 */
    public List<Map<String, Object>> countByArea() {
        return seatMapper.countByArea();
    }

    /**
     * 统计各状态机位数量，用于收银台首页看板。
     * 用 LinkedHashMap 保证展示顺序稳定。
     */
    public Map<String, Integer> countByStatus() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(Constants.SEAT_FREE, 0);
        result.put(Constants.SEAT_USING, 0);
        result.put(Constants.SEAT_MAINTENANCE, 0);
        for (Seat seat : seatMapper.selectList(null)) {
            result.merge(seat.getStatus(), 1, Integer::sum);
        }
        return result;
    }

    /* ==================== 座位图 ==================== */

    /**
     * 组装座位图数据：把「机位状态」与「当前生效预约」合并为四种展示态。
     *
     * <p>展示态优先级：<b>维护中 &gt; 使用中 &gt; 预约中 &gt; 空闲</b>。
     * 使用中优先于预约中，因为顾客正在上机是更强势的事实 —— 某台机器虽然
     * 时段内有预约，但上一批顾客还没下机，收银员此刻该看到的是「使用中」。</p>
     *
     * <p>预约态是<b>实时派生</b>的：只查「当前时刻落在预约时段内」的记录，
     * 所以预约到点会自动由蓝转红（开台）或转绿（过期未到店），无需人工干预。</p>
     *
     * <p>查询用 3 次批量查询代替逐机位循环查询：22 台机位若逐个查就是 66 次
     * 数据库往返，座位图每 5 秒轮询一次会把这个开销放大到不可接受。</p>
     *
     * @return 按区域、机位编号排序的座位图数据
     */
    public List<SeatView> buildBoard() {
        LocalDateTime now = LocalDateTime.now();

        List<Seat> seats = seatMapper.selectList(null);
        if (seats.isEmpty()) {
            return List.of();
        }

        // 一次性取出「在机记录」与「当前生效预约」，再在内存里按 seatId 归并
        Map<Long, SeatSession> sessionBySeat = new HashMap<>();
        for (SeatSession s : sessionMapper.selectUsingList()) {
            if (s.getSeatId() != null) {
                sessionBySeat.put(s.getSeatId(), s);
            }
        }

        Map<Long, Reservation> reservationBySeat = new HashMap<>();
        for (Reservation r : reservationMapper.selectOccurringAt(now)) {
            // 同一机位可能有多条相邻/重叠预约，取结束时间最晚的一条展示
            reservationBySeat.merge(r.getSeatId(), r,
                    (a, b) -> a.getEndTime().isAfter(b.getEndTime()) ? a : b);
        }

        List<SeatView> board = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            board.add(toView(seat, sessionBySeat.get(seat.getId()),
                    reservationBySeat.get(seat.getId()), now));
        }

        board.sort(Comparator.comparing(SeatView::getArea, Comparator.nullsLast(String::compareTo))
                .thenComparing(SeatView::getSeatNo, Comparator.nullsLast(String::compareTo)));
        return board;
    }

    /** 把一台机位及其关联信息组装成座位图单元格 */
    private SeatView toView(Seat seat, SeatSession session, Reservation reservation,
                            LocalDateTime now) {
        SeatView v = new SeatView();
        v.setSeatId(seat.getId());
        v.setSeatNo(seat.getSeatNo());
        v.setArea(seat.getArea());
        v.setConfig(seat.getConfig());
        v.setStatus(seat.getStatus());

        String display;
        if (Constants.SEAT_MAINTENANCE.equals(seat.getStatus())) {
            display = Constants.SEAT_MAINTENANCE;
        } else if (Constants.SEAT_USING.equals(seat.getStatus())) {
            display = Constants.SEAT_USING;
        } else if (reservation != null) {
            display = Constants.SEAT_DISPLAY_RESERVED;
        } else {
            display = Constants.SEAT_FREE;
        }
        v.setDisplayStatus(display);
        v.setDisplayStatusName(displayName(display));
        // 只有空闲才算「可开台」：预约中的机位仍可开台（顾客可能提前到店），
        // 但走的是普通开台流程，由收银员自行判断，前端不把它标成可点开的绿色。
        v.setOpenable(Constants.SEAT_FREE.equals(display));

        if (session != null) {
            v.setSessionId(session.getId());
            v.setMemberName(session.getMemberName());
            v.setStartTime(session.getStartTime());
            v.setElapsed(TimeUtil.elapsedFrom(session.getStartTime()));
        }

        if (reservation != null) {
            v.setReservationId(reservation.getId());
            v.setReservationMemberName(reservation.getMemberName());
            v.setReservationStart(reservation.getStartTime());
            v.setReservationEnd(reservation.getEndTime());
            v.setReservationHint(reservationHint(reservation, now));
        }

        v.setTooltip(buildTooltip(v));
        return v;
    }

    /**
     * 预约时间提示。
     * 分三种情形：尚未到点（还有多久）、刚过到点（等待到店）、已在时段内正常上机时段。
     * 统一按「距到点还有 X 分钟」表述，已过到点则提示等待到店。
     */
    private String reservationHint(Reservation r, LocalDateTime now) {
        long minutes = Duration.between(now, r.getStartTime()).toMinutes();
        if (minutes > 0) {
            if (minutes < 60) {
                return minutes + " 分钟后到点";
            }
            return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟后到点";
        }
        // 已过到点，但仍处于预约时段内
        return "已到点，等待到店";
    }

    /** 拼装鼠标悬停提示，前端直接放进 title 属性 */
    private String buildTooltip(SeatView v) {
        StringBuilder sb = new StringBuilder();
        sb.append("机位 ").append(v.getSeatNo());
        if (v.getArea() != null) {
            sb.append("（").append(v.getArea()).append("）");
        }
        sb.append("\n状态：").append(v.getDisplayStatusName());

        if (v.getSessionId() != null) {
            sb.append("\n会员：").append(nullSafe(v.getMemberName()));
            sb.append("\n已上机：").append(nullSafe(v.getElapsed()));
        }
        if (v.getReservationId() != null) {
            sb.append("\n预约人：").append(nullSafe(v.getReservationMemberName()));
            if (v.getReservationStart() != null && v.getReservationEnd() != null) {
                sb.append("\n预约时段：")
                        .append(v.getReservationStart().toLocalTime().withSecond(0).withNano(0))
                        .append(" - ")
                        .append(v.getReservationEnd().toLocalTime().withSecond(0).withNano(0));
            }
            if (v.getReservationHint() != null) {
                sb.append("\n").append(v.getReservationHint());
            }
        }
        if (v.getConfig() != null && !v.getConfig().isBlank()) {
            sb.append("\n配置：").append(v.getConfig());
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null ? "—" : s;
    }

    /** 展示态中文名（含派生的「预约」） */
    public String displayName(String display) {
        return switch (display) {
            case Constants.SEAT_FREE -> "空闲";
            case Constants.SEAT_USING -> "使用中";
            case Constants.SEAT_MAINTENANCE -> "维修";
            case Constants.SEAT_DISPLAY_RESERVED -> "预约";
            default -> display;
        };
    }

    /**
     * 统计各展示态数量，供座位图图例显示。
     * 与座位图用同一套派生逻辑，避免图例数字和图上格子对不上。
     */
    public Map<String, Integer> countByDisplayStatus() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(Constants.SEAT_FREE, 0);
        result.put(Constants.SEAT_USING, 0);
        result.put(Constants.SEAT_DISPLAY_RESERVED, 0);
        result.put(Constants.SEAT_MAINTENANCE, 0);
        for (SeatView v : buildBoard()) {
            result.merge(v.getDisplayStatus(), 1, Integer::sum);
        }
        return result;
    }

    /* ==================== 机位维护 ==================== */

    /** 新增机位，编号不可重复 */
    @Transactional(rollbackFor = Exception.class)
    public Seat create(Seat seat) {
        if (seat.getSeatNo() == null || seat.getSeatNo().isBlank()) {
            throw new BizException("机位编号不能为空");
        }
        if (seat.getArea() == null || seat.getArea().isBlank()) {
            throw new BizException("所属区域不能为空");
        }
        Long exists = seatMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                .QueryWrapper<Seat>().eq("seat_no", seat.getSeatNo()));
        if (exists != null && exists > 0) {
            throw new BizException("机位编号「" + seat.getSeatNo() + "」已存在");
        }
        seat.setId(null);
        seat.setStatus(Constants.SEAT_FREE);
        seatMapper.insert(seat);
        log.info("新增机位 编号={} 区域={}", seat.getSeatNo(), seat.getArea());
        return seat;
    }

    /**
     * 修改机位资料（编号、区域、配置）。
     * 注意：改动区域会直接改变该机位结算时适用的计费规则，属于实质变更。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Seat form) {
        Seat seat = lockById(form.getId());

        if (form.getSeatNo() != null && !form.getSeatNo().equals(seat.getSeatNo())) {
            Long exists = seatMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                    .QueryWrapper<Seat>().eq("seat_no", form.getSeatNo()));
            if (exists != null && exists > 0) {
                throw new BizException("机位编号「" + form.getSeatNo() + "」已被占用");
            }
            seat.setSeatNo(form.getSeatNo());
        }
        if (form.getArea() != null && !form.getArea().isBlank()) {
            seat.setArea(form.getArea());
        }
        if (form.getConfig() != null) {
            seat.setConfig(form.getConfig());
        }
        seatMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Seat>()
                .eq(Seat::getId, seat.getId()).set(Seat::getSeatNo, seat.getSeatNo())
                .set(Seat::getArea, seat.getArea()).set(Seat::getConfig, seat.getConfig()));
        log.info("修改机位 编号={} 区域={}", seat.getSeatNo(), seat.getArea());
    }

    /* ==================== 状态流转 ==================== */

    /**
     * 机位状态流转。会先校验流转合法性，再以 CAS 方式落库。
     *
     * @param seatId 机位ID
     * @param target 目标状态
     * @param reason 变更原因，写入日志便于追溯
     */
    @Transactional(rollbackFor = Exception.class)
    public void transit(Long seatId, String target, String reason) {
        Seat seat = lockById(seatId);
        String current = seat.getStatus();

        if (current.equals(target)) {
            log.debug("机位 {} 已处于 {} 状态，无需变更", seat.getSeatNo(), target);
            return;
        }

        if (Constants.SEAT_USING.equals(current) || Constants.SEAT_USING.equals(target)) {
            throw new BizException("不允许手工切换使用中状态，请通过开台或下机结算办理");
        }
        Long active = sessionMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SeatSession>()
                .eq("seat_id", seatId).eq("status", Constants.SESSION_USING));
        if (active != null && active > 0) throw new BizException("机位仍有未结算记录，不能手工变更状态");
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BizException(String.format("机位 %s 当前为「%s」，不允许直接切换为「%s」",
                    seat.getSeatNo(), name(current), name(target)));
        }

        casStatus(seatId, current, target, seat.getSeatNo(), reason);
    }

    /**
     * 以 CAS（Compare-And-Swap）方式更新状态。
     *
     * 为什么不用「先查询再更新」：两名收银员同时给同一机位开台时，
     * 双方都会查到 FREE，随后各自执行更新，结果一台机器被开两次。
     * 把状态条件放进 UPDATE 的 WHERE 子句，由数据库保证判断与修改是原子的：
     * 受影响行数为 1 表示抢占成功，为 0 表示期间状态已被他人改变。
     */
    public void casStatus(Long seatId, String expectStatus, String targetStatus,
                          String seatNo, String reason) {
        int rows = seatMapper.casStatus(seatId, expectStatus, targetStatus);
        if (rows == 0) {
            throw new BizException(String.format(
                    "机位 %s 的状态已被其他操作改变，请刷新后重试", seatNo == null ? seatId : seatNo));
        }
        log.info("机位状态变更 机位={} {} -> {} 原因={}",
                seatNo == null ? seatId : seatNo, expectStatus, targetStatus, reason);
    }

    public String name(String status) {
        return STATUS_NAME.getOrDefault(status, status);
    }
}
