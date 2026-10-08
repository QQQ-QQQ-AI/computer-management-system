package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.PasswordUtil;
import com.cafe.common.ValidateUtil;
import com.cafe.entity.Member;
import com.cafe.entity.MemberLevel;
import com.cafe.entity.RechargeRecord;
import com.cafe.mapper.MemberLevelMapper;
import com.cafe.mapper.MemberMapper;
import com.cafe.mapper.RechargeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

/**
 * 会员服务：注册、开卡、查询、状态管理、充值。
 *
 * 关于余额：本类不直接修改 member.balance，一律通过 BalanceService 完成，
 * 以保证每一笔变动都有对应流水、账目可随时重算校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final RechargeRecordMapper rechargeRecordMapper;
    private final BalanceService balanceService;

    /* ==================== 查询 ==================== */

    /** 分页查询会员（联表带出等级名称与折扣） */
    public IPage<Member> page(long pageNo, long pageSize, String keyword, Integer status) {
        return memberMapper.selectDetailPage(new Page<>(pageNo, pageSize), keyword, status);
    }

    /** 按ID查询会员，带等级信息（计费需要等级折扣） */
    public Member getById(Long id) {
        Member member = memberMapper.selectWithLevelById(id);
        if (member == null) {
            throw new BizException("会员不存在，ID=" + id);
        }
        return member;
    }

    /** 按手机号查询，会员登录使用 */
    public Member getByPhone(String phone) {
        return memberMapper.selectByPhone(phone);
    }

    /** 按卡号查询，收银台凭卡办理业务 */
    public Member getByCardNo(String cardNo) {
        Member member = memberMapper.selectByCardNo(cardNo);
        if (member == null) {
            throw new BizException("未找到卡号为「" + cardNo + "」的会员");
        }
        return member;
    }

    /** 全部会员等级配置，用于下拉选择 */
    public List<MemberLevel> listLevels() {
        return memberLevelMapper.selectList(
                new LambdaQueryWrapper<MemberLevel>().orderByAsc(MemberLevel::getLevel));
    }

    public Member lockById(Long id) {
        Member member = memberMapper.selectForUpdate(id);
        if (member == null) throw new BizException("会员不存在，ID=" + id);
        MemberLevel level = memberLevelMapper.selectById(member.getLevel());
        member.setLevelDiscount(level == null ? BigDecimal.ONE : level.getDiscount());
        member.setLevelName(level == null ? "未配置" : level.getLevelName());
        return member;
    }

    /* ==================== 注册与开卡 ==================== */

    /**
     * 会员自助注册。
     * 按需求说明书，自助注册仅需手机号与密码，不采集身份证；
     * 身份证在收银员开卡环节补录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Member register(String phone, String rawPassword, String name) {
        if (!ValidateUtil.isPhone(phone)) {
            throw new BizException("手机号格式不正确");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        if (getByPhone(phone) != null) {
            throw new BizException("该手机号已注册，请直接登录");
        }

        Member member = new Member();
        member.setCardNo(generateCardNo());
        member.setName(name == null || name.isBlank() ? "会员" + phone.substring(phone.length() - 4) : name.trim());
        member.setPhone(phone);
        member.setPassword(PasswordUtil.encrypt(rawPassword));
        member.setBalance(BigDecimal.ZERO);
        member.setDeposit(BigDecimal.ZERO);
        member.setLevel(1);                 // 注册即为普通会员
        member.setStatus(Constants.ENABLED);
        memberMapper.insert(member);

        log.info("会员自助注册成功 卡号={} 手机号={}", member.getCardNo(), phone);
        return member;
    }

    /**
     * 收银员开卡。
     * 需录入姓名、身份证号与电话，校验身份证格式与校验码并生成卡号。
     *
     * @param initialAmount 开卡时的初始充值金额，可为 0 或 null
     * @param operatorId    经办收银员ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Member openCard(String name, String idCard, String phone, Integer level,
                           String rawPassword, BigDecimal initialAmount, Long operatorId) {
        if (name == null || name.isBlank()) {
            throw new BizException("姓名不能为空");
        }
        if (!ValidateUtil.isIdCard(idCard)) {
            throw new BizException("身份证号格式不正确或校验位不匹配，请核对后重新输入");
        }
        if (!ValidateUtil.isPhone(phone)) {
            throw new BizException("手机号格式不正确");
        }
        if (getByPhone(phone) != null) {
            throw new BizException("该手机号已被其他会员使用");
        }
        if (rawPassword != null && !rawPassword.isBlank() && rawPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }

        Member member = new Member();
        member.setCardNo(generateCardNo());
        member.setName(name.trim());
        member.setIdCard(idCard.toUpperCase());
        member.setPhone(phone);
        // 未指定初始密码时用手机号后 6 位，收银员应提示顾客首次登录后修改
        member.setPassword(PasswordUtil.encrypt(
                (rawPassword == null || rawPassword.isBlank()) ? phone.substring(5) : rawPassword));
        member.setBalance(BigDecimal.ZERO);
        member.setDeposit(BigDecimal.ZERO);
        if (level != null && memberLevelMapper.selectById(level) == null) throw new BizException("会员等级不存在");
        if (initialAmount != null && initialAmount.signum() < 0) throw new BizException("初始充值不能为负数");
        member.setLevel(level == null ? 1 : level);
        member.setStatus(Constants.ENABLED);
        memberMapper.insert(member);

        log.info("开卡成功 卡号={} 姓名={} 经办={}", member.getCardNo(), name, operatorId);

        // 开卡同时充值：复用充值流程，保证有充值记录与账户流水
        if (initialAmount != null && initialAmount.signum() > 0) {
            recharge(member.getId(), initialAmount, Constants.RECHARGE_CASH, operatorId);
        }
        return getById(member.getId());
    }

    /** 生成卡号：M + 年份 + 16位随机标识，避免并发MAX编号碰撞 */
    public String generateCardNo() {
        return "M" + Year.now().getValue() + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /* ==================== 充值 ==================== */

    /**
     * 会员充值。同时写入充值记录与账户流水。
     *
     * @param type 充值方式：ONLINE 线上自助 / CASH 收银台现金
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal recharge(Long memberId, BigDecimal amount, String type, Long operatorId) {
        if (!ValidateUtil.isPositiveAmount(amount)) {
            throw new BizException("充值金额必须大于 0");
        }
        if (!ValidateUtil.isMoneyScaleValid(amount)) {
            throw new BizException("充值金额最多保留两位小数");
        }
        // 先持有会员排他锁，再插入带会员外键的充值记录，避免并发共享锁升级死锁。
        Member member = lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，无法充值");
        }

        boolean online = Constants.RECHARGE_ONLINE.equals(type);

        // 先落充值记录，拿到主键后作为流水的关联业务ID
        RechargeRecord record = new RechargeRecord();
        record.setMemberId(memberId);
        record.setAmount(amount);
        record.setType(type);
        record.setOperatorId(operatorId);
        rechargeRecordMapper.insert(record);

        // 再通过统一入口改余额并写流水
        BigDecimal after = balanceService.increase(memberId, Constants.BAL_RECHARGE, amount,
                "RECHARGE", record.getId(),
                online ? "会员线上自助充值" : "收银台现金充值，经办员工ID=" + operatorId,
                operatorId);

        log.info("充值成功 会员={} 金额={} 方式={} 余额={}", memberId, amount, type, after);
        return after;
    }

    /** 分页查询充值流水 */
    public IPage<RechargeRecord> pageRecharge(long pageNo, long pageSize, Long memberId) {
        return rechargeRecordMapper.selectDetailPage(new Page<>(pageNo, pageSize), memberId);
    }

    /* ==================== 状态与资料管理 ==================== */

    /** 冻结或解冻会员账户 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long memberId, int status) {
        Member member = lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == status) {
            return;
        }
        if (status != Constants.ENABLED && status != Constants.DISABLED) throw new BizException("账户状态无效");
        member.setStatus(status);
        memberMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member>()
                .eq(Member::getId, member.getId()).set(Member::getStatus, status));
        log.info("会员状态变更 会员={} {} -> {}", memberId, member.getStatus(), status);
    }

    /** 修改会员资料（姓名、电话） */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long memberId, String name, String phone, Integer level) {
        Member member = lockById(memberId);
        if (name != null && !name.isBlank()) {
            member.setName(name.trim());
        }
        if (phone != null && !phone.isBlank() && !phone.equals(member.getPhone())) {
            if (!ValidateUtil.isPhone(phone)) {
                throw new BizException("手机号格式不正确");
            }
            if (getByPhone(phone) != null) {
                throw new BizException("该手机号已被其他会员使用");
            }
            member.setPhone(phone);
        }
        if (level != null) {
            if (memberLevelMapper.selectById(level) == null) throw new BizException("会员等级不存在");
            member.setLevel(level);
        }
        memberMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member>()
                .eq(Member::getId, member.getId()).set(Member::getName, member.getName())
                .set(Member::getPhone, member.getPhone()).set(Member::getLevel, member.getLevel()));
    }

    /** 重置会员密码 */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long memberId, String oldPassword, String newPassword) {
        Member member = lockById(memberId);
        if (!PasswordUtil.matches(oldPassword, member.getPassword())) throw new BizException("原密码不正确");
        resetPassword(memberId, newPassword);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long memberId, String newRawPassword) {
        if (newRawPassword == null || newRawPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        Member member = lockById(memberId);
        member.setPassword(PasswordUtil.encrypt(newRawPassword));
        memberMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member>()
                .eq(Member::getId, member.getId()).set(Member::getPassword, member.getPassword()));
        log.info("会员密码已重置 会员={}", memberId);
    }
}
