package com.cafe.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.BalanceRecord;
import com.cafe.entity.Member;
import com.cafe.mapper.BalanceRecordMapper;
import com.cafe.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 账户余额与流水服务。
 *
 * 设计原则：这是全系统「余额变动」的唯一入口。
 * 任何业务（充值、上机结算、商品消费、押金冻结解冻）都不得直接修改 member.balance，
 * 必须调用本服务的 change 方法，由它统一完成三件事：
 *     加行锁读取 → 校验并更新余额 → 写入一条流水
 * 三者处于同一事务，要么全部成功，要么全部回滚。
 *
 * 这样带来的收益是「余额始终可由流水重算校验」：
 *     SUM(balance_record.change_amount) == member.balance
 * 一旦出现差异就能立刻发现账务异常，这是验证结算数据一致性的基础。
 *
 * 若绕过本服务直接改余额，上述等式即被破坏，对账将失去意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final MemberMapper memberMapper;
    private final BalanceRecordMapper balanceRecordMapper;

    /**
     * 执行一次余额变动并记录流水。
     *
     * @param memberId      会员ID
     * @param changeType    变动类型，取值见 Constants.BAL_*
     * @param signedAmount  带符号金额：正数为增加，负数为减少
     * @param bizType       关联业务类型：SESSION / RENTAL 等，可为空
     * @param bizId         关联业务主键，可为空
     * @param remark        备注
     * @param operatorId    经办员工ID，会员自助操作时传 null
     * @return 变动后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal change(Long memberId,
                             String changeType,
                             BigDecimal signedAmount,
                             String bizType,
                             Long bizId,
                             String remark,
                             Long operatorId) {
        return doChange(memberId, changeType, signedAmount, BigDecimal.ZERO,
                bizType, bizId, remark, operatorId);
    }

    /**
     * 冻结押金：余额减少、冻结押金增加。
     *
     * 押金不是消费，而是从可用余额划转到冻结额度，顾客归还设备后应原额退回，
     * 因此余额减少的同时必须等额记入 member.deposit，两处一起变才不会凭空少钱。
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal freezeDeposit(Long memberId, BigDecimal amount,
                                    String bizType, Long bizId, String remark, Long operatorId) {
        BigDecimal amt = amount.abs();
        return doChange(memberId, Constants.BAL_DEPOSIT_FREEZE, amt.negate(), amt,
                bizType, bizId, remark, operatorId);
    }

    /** 解冻押金：冻结押金减少、余额等额恢复 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal unfreezeDeposit(Long memberId, BigDecimal amount,
                                      String bizType, Long bizId, String remark, Long operatorId) {
        BigDecimal amt = amount.abs();
        return doChange(memberId, Constants.BAL_DEPOSIT_UNFREEZE, amt, amt.negate(),
                bizType, bizId, remark, operatorId);
    }

    /**
     * 余额与押金变动的统一实现。
     *
     * @param signedAmount 余额变动量，正数增加、负数减少
     * @param depositDelta 冻结押金变动量，正数冻结、负数解冻；普通消费传 0
     */
    private BigDecimal doChange(Long memberId,
                                String changeType,
                                BigDecimal signedAmount,
                                BigDecimal depositDelta,
                                String bizType,
                                Long bizId,
                                String remark,
                                Long operatorId) {

        if (signedAmount == null || signedAmount.signum() == 0) {
            throw new BizException("变动金额必须为非零值");
        }

        // 1. 加行锁读取，把并发的资金操作串行化
        Member member = memberMapper.selectForUpdate(memberId);
        if (member == null) {
            throw new BizException("会员不存在，ID=" + memberId);
        }
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED
                && !("SESSION".equals(bizType) || "RENTAL".equals(bizType))) {
            throw new BizException("会员「" + member.getName() + "」账户已冻结，无法进行资金操作");
        }

        // 2. 计算并校验余额
        BigDecimal before = nz(member.getBalance());
        BigDecimal after = before.add(signedAmount);
        if (after.signum() < 0) {
            throw new BizException(String.format(
                    "余额不足：当前余额 %s 元，本次需扣减 %s 元",
                    before.toPlainString(), signedAmount.abs().toPlainString()));
        }

        // 冻结押金不足时解冻会让押金变负，属于数据异常，必须拦下
        BigDecimal depositBefore = nz(member.getDeposit());
        BigDecimal depositAfter = depositBefore.add(depositDelta);
        if (depositAfter.signum() < 0) {
            throw new BizException("冻结押金不足，当前冻结 " + depositBefore.toPlainString()
                    + " 元，本次需解冻 " + depositDelta.abs().toPlainString() + " 元");
        }

        member.setBalance(after);
        member.setDeposit(depositAfter);
        memberMapper.updateById(member);

        // 3. 写流水，前后余额与本次变动严格对应
        BalanceRecord record = new BalanceRecord();
        record.setMemberId(memberId);
        record.setChangeType(changeType);
        record.setChangeAmount(signedAmount);
        record.setBeforeBalance(before);
        record.setAfterBalance(after);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setRemark(remark);
        record.setOperatorId(operatorId);
        balanceRecordMapper.insert(record);

        log.info("余额变动 会员={} 类型={} 金额={} 余额 {} -> {}{}",
                memberId, changeType, signedAmount.toPlainString(),
                before.toPlainString(), after.toPlainString(),
                depositDelta.signum() == 0 ? ""
                        : String.format("，冻结押金 %s -> %s",
                        depositBefore.toPlainString(), depositAfter.toPlainString()));
        return after;
    }

    /* ==================== 业务语义的便捷封装 ==================== */

    /** 入账：充值、押金解冻等增加余额的操作 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal increase(Long memberId, String changeType, BigDecimal amount,
                               String bizType, Long bizId, String remark, Long operatorId) {
        return change(memberId, changeType, amount.abs(), bizType, bizId, remark, operatorId);
    }

    /** 出账：上机费、商品消费、租金、押金冻结等减少余额的操作 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal decrease(Long memberId, String changeType, BigDecimal amount,
                               String bizType, Long bizId, String remark, Long operatorId) {
        return change(memberId, changeType, amount.abs().negate(), bizType, bizId, remark, operatorId);
    }

    /**
     * 分页查询账户流水。
     *
     * @param memberId   会员ID；传 null 表示查询全部（管理端对账用）
     * @param changeType 变动类型过滤，可为空
     */
    public IPage<BalanceRecord> pageRecords(long pageNo, long pageSize,
                                            Long memberId, String changeType) {
        return balanceRecordMapper.selectDetailPage(
                new Page<>(pageNo, pageSize), memberId, changeType);
    }

    /**
     * 对账查询：按流水累加重算该会员余额。
     * 与 member.balance 比对即可判断账务是否一致。
     *
     * @return 流水累计金额；与当前余额相等表示一致
     */
    public BigDecimal recalculate(Long memberId) {
        BigDecimal sum = balanceRecordMapper.sumByMember(memberId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * 校验某会员账务是否一致。
     *
     * @return 差额；为 0 表示余额与流水完全吻合
     */
    public BigDecimal checkConsistency(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException("会员不存在，ID=" + memberId);
        }
        return nz(member.getBalance()).subtract(recalculate(memberId));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
