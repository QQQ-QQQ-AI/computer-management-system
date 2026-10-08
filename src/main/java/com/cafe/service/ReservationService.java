package com.cafe.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.Member;
import com.cafe.entity.Reservation;
import com.cafe.entity.Seat;
import com.cafe.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 预约服务：在线预约、冲突检测、核销、超时作废。
 *
 * 冲突检测规则：同一机位，状态为「待核销」或「已核销」的预约中，
 * 若存在时间段重叠即视为冲突。判定条件为
 *     新开始 &lt; 已有结束  且  新结束 &gt; 已有开始
 * 该写法对「首尾相接」不判冲突：10:00-12:00 与 12:00-14:00 可以共存。
 *
 * 由于机位不设「已预约」状态，预约超时作废时无需释放机位，
 * 也就不会出现「预约作废了但机位还锁着」这类状态不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReservationMapper reservationMapper;
    private final SeatService seatService;
    private final MemberService memberService;
    private final SessionService sessionService;

    /* ==================== 查询 ==================== */

    public IPage<Reservation> page(long pageNo, long pageSize, Long memberId, String status) {
        return reservationMapper.selectDetailPage(new Page<>(pageNo, pageSize), memberId, status);
    }

    public Reservation getById(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BizException("预约记录不存在，ID=" + id);
        }
        return reservation;
    }

    /**
     * 会员当前有效的预约（待核销且未过期）。
     * 走联表查询以带出机位编号与区域，页面才能显示「A05 普通区」而不是空白。
     */
    public List<Reservation> listActiveByMember(Long memberId) {
        return reservationMapper.selectActiveByMember(memberId);
    }

    /* ==================== 提交预约 ==================== */

    /**
     * 提交预约。会校验时段合法性、机位可用性与时段冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public Reservation create(Long memberId, Long seatId, LocalDateTime start, LocalDateTime end) {
        Member member = memberService.lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，无法预约");
        }

        Seat seat = seatService.lockById(seatId);
        if (Constants.SEAT_MAINTENANCE.equals(seat.getStatus())) {
            throw new BizException("机位 " + seat.getSeatNo() + " 正在维护，暂不可预约");
        }

        if (start == null || end == null) {
            throw new BizException("请选择预约的开始与结束时间");
        }
        if (!start.isBefore(end)) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        if (end.isBefore(LocalDateTime.now())) {
            throw new BizException("所选时段已经过去，请重新选择");
        }

        // 时段冲突检测，排除自身（新增时传 null）
        if (!end.isAfter(LocalDateTime.now())) throw new BizException("预约时段已结束");
        if (!start.isAfter(LocalDateTime.now()) && Constants.SEAT_USING.equals(seat.getStatus())) throw new BizException("该机位当前正在使用，不能预约当前时段");
        int conflict = reservationMapper.selectConflictsForUpdate(seatId, start, end).size();
        if (conflict > 0) {
            throw new BizException(String.format(
                    "机位 %s 在 %s 至 %s 时段已被预约，请另选时段或更换机位",
                    seat.getSeatNo(), start.format(TIME_FMT), end.format(TIME_FMT)));
        }

        Reservation reservation = new Reservation();
        reservation.setCode(generateCode());
        reservation.setMemberId(memberId);
        reservation.setSeatId(seatId);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(Constants.RESV_PENDING);
        reservationMapper.insert(reservation);

        log.info("预约提交成功 单号={} 会员={} 机位={} {} - {}",
                reservation.getCode(), member.getCardNo(), seat.getSeatNo(),
                start.format(TIME_FMT), end.format(TIME_FMT));
        return reservation;
    }

    /**
     * 生成预约单号：R + 日期 + 10位随机标识。
     * 不使用MAX推导流水，避免同时提交预约发生编号碰撞。
     */
    public String generateCode() {
        return "R" + LocalDate.now().format(CODE_FMT) + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /* ==================== 取消与核销 ==================== */

    /**
     * 取消预约。会员只能取消本人的预约，且需在到店时间之前。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reservationId, Long memberId) {
        Reservation initial = getById(reservationId);
        memberService.lockById(initial.getMemberId());
        seatService.lockById(initial.getSeatId());
        Reservation reservation = reservationMapper.selectForUpdate(reservationId);

        if (memberId != null && !memberId.equals(reservation.getMemberId())) {
            throw new BizException("只能取消本人的预约");
        }
        if (!Constants.RESV_PENDING.equals(reservation.getStatus())) {
            throw new BizException("该预约当前为「" + reservation.getStatusName() + "」，无法取消");
        }
        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException("预约已到到店时间，无法自行取消，请联系前台处理");
        }

        reservation.setStatus(Constants.RESV_CANCELED);
        reservationMapper.updateById(reservation);
        log.info("预约已取消 单号={}", reservation.getCode());
    }

    /**
     * 预约核销：在同一事务中为原会员和原机位开台，再标记已核销。
     */
    @Transactional(rollbackFor = Exception.class)
    public Reservation verify(Long reservationId, Long operatorId) {
        Reservation initial = getById(reservationId);
        memberService.lockById(initial.getMemberId());
        seatService.lockById(initial.getSeatId());
        Reservation reservation = reservationMapper.selectForUpdate(reservationId);

        if (!Constants.RESV_PENDING.equals(reservation.getStatus())) {
            throw new BizException("该预约当前为「" + reservation.getStatusName() + "」，无法核销");
        }
        if (reservation.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BizException("该预约时段已结束，无法核销");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(reservation.getStartTime().minusMinutes(15))) throw new BizException("只能在预约开始前15分钟内办理核销开台");
        if (!now.isBefore(reservation.getEndTime()) || now.isAfter(reservation.getStartTime().plusMinutes(15))) throw new BizException("预约已超过15分钟到店宽限期");
        sessionService.open(reservation.getMemberId(), reservation.getSeatId(), operatorId);
        reservation.setStatus(Constants.RESV_USED);
        reservationMapper.updateById(reservation);
        log.info("预约核销成功 单号={} 经办={}", reservation.getCode(), operatorId);
        return reservation;
    }

    /**
     * 将超时未到店的预约置为已过期。
     *
     * 由定时任务周期性调用。机位不设「已预约」状态，
     * 因此这里只需改预约状态，不涉及任何机位释放动作。
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    @Transactional(rollbackFor = Exception.class)
    public void expireTimeout() {
        int count = reservationMapper.expirePending();
        if (count > 0) log.info("已将 {} 条超时预约置为已过期", count);
    }
}
