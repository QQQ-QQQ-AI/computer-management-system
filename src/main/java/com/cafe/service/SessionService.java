package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.dto.BillingResult;
import com.cafe.entity.BillingRule;
import com.cafe.entity.Member;
import com.cafe.entity.Seat;
import com.cafe.entity.SeatSession;
import com.cafe.mapper.BillingRuleMapper;
import com.cafe.mapper.ProductOrderMapper;
import com.cafe.mapper.SeatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 上机服务：开台与下机结算。
 *
 * 本类是「机位状态」「计费规则」「会员余额」「账户流水」四者交汇的地方，
 * 也是系统中最需要保证一致性的业务。核心约束：
 *
 * 1. 开台与结算是机位状态的唯一驱动来源，状态切换一律走 SeatService 的 CAS，
 *    绝不出现「先查状态再更新」的写法。
 *
 * 2. 结算是一个整体事务：算费 → 扣款 → 生成流水 → 更新记录 → 释放机位。
 *    五步要么全部成功，要么全部回滚。任一步失败都不能留下
 *    「钱扣了但机位没释放」或「机位释放了但钱没扣」这类中间状态。
 *
 * 3. 扣款余额不足时直接拒绝并提示差额，绝不把余额扣成负数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SeatSessionMapper sessionMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final ProductOrderMapper productOrderMapper;
    private final SeatService seatService;
    private final MemberService memberService;
    private final BillingService billingService;
    private final BalanceService balanceService;
    private final com.cafe.mapper.ReservationMapper reservationMapper;

    /* ==================== 查询 ==================== */

    public IPage<SeatSession> page(long pageNo, long pageSize, Long memberId, String status) {
        return sessionMapper.selectDetailPage(new Page<>(pageNo, pageSize), memberId, status);
    }

    /** 当前所有使用中的记录，收银台「下机结算」列表 */
    public List<SeatSession> listUsing() {
        return sessionMapper.selectUsingList();
    }

    public SeatSession getById(Long id) {
        SeatSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BizException("上机记录不存在，ID=" + id);
        }
        return session;
    }

    /** 会员历史累计上机费（全部已结算记录），供会员端展示累计消费 */
    public BigDecimal sumHourFeeTotal(Long memberId) {
        BigDecimal sum = sessionMapper.sumHourFeeTotal(memberId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * 查询某会员当前是否正在上机。
     * 走联表查询以带出机位编号与区域，供页面直接展示。
     */
    public SeatSession findActiveByMember(Long memberId) {
        return sessionMapper.selectActiveByMember(memberId);
    }

    public SeatSession lockSession(Long id) {
        SeatSession session = sessionMapper.selectForUpdate(id);
        if (session == null) throw new BizException("上机记录不存在");
        return session;
    }

    public SeatSession lockActiveByMember(Long memberId) {
        return sessionMapper.selectActiveForUpdate(memberId);
    }

    /* ==================== 开台上机 ==================== */

    /**
     * 开台上机：为会员分配空闲机位并记录开台时间。
     *
     * 执行顺序刻意设计为「先抢占机位、后写记录」：
     * 机位 CAS 是唯一可能因并发失败的一步，把它放在最前面，
     * 失败时不需要回滚任何已写入的数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatSession open(Long memberId, Long seatId, Long operatorId) {
        Member member = memberService.lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，无法开台");
        }

        Seat seat = seatService.lockById(seatId);
        if (!Constants.SEAT_FREE.equals(seat.getStatus())) {
            throw new BizException(String.format("机位 %s 当前为「%s」，不可开台",
                    seat.getSeatNo(), seatService.name(seat.getStatus())));
        }

        // 同一会员不允许同时开多台，否则结算时无法判断该结算哪一条
        SeatSession active = sessionMapper.selectActiveForUpdate(memberId);
        if (active != null) {
            throw new BizException(String.format("该会员已有进行中的上机记录（记录号 %d），请先下机结算",
                    active.getId()));
        }

        // 余额为 0 无法完成后续结算，提前拦截，避免顾客上机后无法结账
        if (member.getBalance() == null || member.getBalance().signum() <= 0) {
            throw new BizException("该会员余额为 " + member.getBalance()
                    + " 元，请先充值后再开台");
        }

        LocalDateTime openingTime = LocalDateTime.now();
        var currentReservations = reservationMapper.selectCurrentForUpdate(seatId, openingTime);
        for (com.cafe.entity.Reservation reservation : currentReservations) {
            if (!memberId.equals(reservation.getMemberId())) {
                throw new BizException("该机位当前时段已被其他会员预约，请更换机位");
            }
            if (Constants.RESV_PENDING.equals(reservation.getStatus())
                    && openingTime.isAfter(reservation.getStartTime().plusMinutes(15))) {
                throw new BizException("预约已超过15分钟到店宽限期，请待预约过期后重新办理开台");
            }
        }
        // 第一步：CAS 抢占机位，并发下抢占失败会在此抛出
        seatService.casStatus(seatId, Constants.SEAT_FREE, Constants.SEAT_USING,
                seat.getSeatNo(), "开台上机，会员=" + member.getCardNo());

        // 第二步：写入上机记录
        SeatSession session = new SeatSession();
        session.setMemberId(memberId);
        session.setSeatId(seatId);
        session.setStartTime(LocalDateTime.now());
        session.setHourFee(BigDecimal.ZERO);
        session.setProductFee(BigDecimal.ZERO);
        session.setTotalFee(BigDecimal.ZERO);
        session.setStatus(Constants.SESSION_USING);
        sessionMapper.insert(session);
        // 普通开台也要核销本人的当前预约，避免到店后仍被定时任务判定为爽约。
        for (com.cafe.entity.Reservation reservation : currentReservations) {
            if (Constants.RESV_PENDING.equals(reservation.getStatus())) {
                reservation.setStatus(Constants.RESV_USED);
                reservationMapper.updateById(reservation);
            }
        }

        log.info("开台成功 记录号={} 会员={} 机位={} 经办={}",
                session.getId(), member.getCardNo(), seat.getSeatNo(), operatorId);
        return session;
    }

    /* ==================== 下机结算 ==================== */

    /**
     * 预估费用：下机前查看应收金额，不产生任何写操作。
     */
    public BillingResult estimate(Long sessionId) {
        SeatSession session = getById(sessionId);
        if (!Constants.SESSION_USING.equals(session.getStatus())) {
            throw new BizException("该记录已结算，无法预估");
        }
        Seat seat = seatService.getById(session.getSeatId());
        Member member = memberService.getById(session.getMemberId());
        BillingRule rule = requireRule(seat.getArea());
        int minutes = elapsedMinutes(session.getStartTime(), LocalDateTime.now());
        return billingService.calculate(minutes, rule, member);
    }

    /**
     * 下机结算：按计费规则算费、扣款、生成流水、更新记录、释放机位。
     *
     * 全部在一个事务内完成。任一环节失败都会整体回滚，
     * 不会出现「已扣款但机位仍占用」或「机位已释放但未扣款」的情况。
     *
     * @return 已更新的上机记录（含费用明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatSession checkout(Long sessionId, Long operatorId) {
        SeatSession initial = getById(sessionId);
        Member member = memberService.lockById(initial.getMemberId());
        SeatSession session = lockSession(sessionId);
        if (!Constants.SESSION_USING.equals(session.getStatus())) {
            throw new BizException("该记录已于 " + session.getEndTime() + " 结算，请勿重复操作");
        }

        Seat seat = seatService.lockById(session.getSeatId());
        BillingRule rule = requireRule(seat.getArea());

        LocalDateTime endTime = LocalDateTime.now();
        int minutes = elapsedMinutes(session.getStartTime(), endTime);

        // 1. 计算上机费
        BillingResult billing = billingService.calculate(minutes, rule, member);

        // 2. 汇总本场次期间的商品消费（下机时与上机费一并结清）
        BigDecimal productFee = productOrderMapper.selectAmountsForUpdate(sessionId).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (productFee == null) {
            productFee = BigDecimal.ZERO;
        }
        BigDecimal totalFee = billing.getFinalFee().add(productFee);

        // 3. 余额校验：提前算出差额给出明确提示，而不是笼统报「余额不足」
        BigDecimal balance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        if (balance.compareTo(totalFee) < 0) {
            throw new BizException(String.format(
                    "余额不足：本次应收 %s 元（上机费 %s + 商品 %s），账户余额 %s 元，还差 %s 元，"
                            + "请先为会员充值后再结算",
                    totalFee.toPlainString(), billing.getFinalFee().toPlainString(),
                    productFee.toPlainString(), balance.toPlainString(),
                    totalFee.subtract(balance).toPlainString()));
        }

        // 4. 扣款并写账户流水，分两笔以便按类型统计各项收入。
        //
        //    金额为 0 时必须跳过：BalanceService 拒绝零金额变动，
        //    若照常调用会抛异常并回滚整个事务，导致机位无法释放、会员被卡在「使用中」。
        //    0 元结算本就不产生资金流动，也不应留下无意义的流水记录。
        if (productFee.signum() > 0) {
            balanceService.decrease(session.getMemberId(), Constants.BAL_PRODUCT, productFee,
                    "SESSION", sessionId, "随上机结算的商品消费", operatorId);
        }
        if (billing.getFinalFee().signum() > 0) {
            balanceService.decrease(session.getMemberId(), Constants.BAL_HOUR_FEE,
                    billing.getFinalFee(), "SESSION", sessionId, "上机费结算", operatorId);
        }

        // 5. 更新上机记录
        session.setEndTime(endTime);
        session.setDurationMinutes(billing.getBillableMinutes());
        session.setHourFee(billing.getFinalFee());
        session.setProductFee(productFee);
        session.setTotalFee(totalFee);
        session.setStatus(Constants.SESSION_FINISHED);
        sessionMapper.updateById(session);

        // 6. 释放机位（CAS，若状态被人为改动过会明显暴露出来）
        seatService.casStatus(session.getSeatId(), Constants.SEAT_USING, Constants.SEAT_FREE,
                seat.getSeatNo(), "下机结算，记录号=" + sessionId);

        log.info("""
                        下机结算完成 记录号={} 会员={} 机位={} 时长={}分钟
                          计费说明：{}
                          上机费={} 商品费={} 合计={}""",
                sessionId, member.getCardNo(), seat.getSeatNo(), billing.getBillableMinutes(),
                billing.getExplain(), billing.getFinalFee().toPlainString(),
                productFee.toPlainString(), totalFee.toPlainString());

        return session;
    }

    /* ==================== 内部工具 ==================== */

    /** 取区域计费规则，未配置直接报错而不是默认按 0 元结算 */
    private BillingRule requireRule(String area) {
        BillingRule rule = billingRuleMapper.selectOne(new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getArea, area)
                .eq(BillingRule::getStatus, Constants.ENABLED)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BizException("区域「" + area + "」尚未配置计费规则，无法结算，请先在后台配置");
        }
        return rule;
    }

    /**
     * 计算实际上机分钟数。
     * 秒数先向上取整到分钟，再由 BillingService 向上取整到 5 分钟，
     * 两步合并的效果等价于「上机秒数直接向上取整到 5 分钟」。
     */
    private int elapsedMinutes(LocalDateTime start, LocalDateTime end) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) Math.ceil(seconds / 60.0);
    }
}
