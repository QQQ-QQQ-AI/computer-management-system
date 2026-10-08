package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.common.PasswordUtil;
import com.cafe.entity.Member;
import com.cafe.entity.SysUser;
import com.cafe.mapper.MemberMapper;
import com.cafe.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务：会员与员工两套登录入口。
 *
 * 安全约定：登录失败时统一提示「账号或密码错误」，不区分「账号不存在」与「密码错误」，
 * 避免被用来枚举系统内已存在的手机号或账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final MemberMapper memberMapper;
    private final MemberService memberService;

    /**
     * 员工登录（收银员 / 管理员）。
     *
     * @return 登录态载体，供写入 Session
     */
    @org.springframework.transaction.annotation.Transactional
    public LoginUser loginStaff(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            throw new BizException("请输入账号和密码");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));

        if (user == null || !PasswordUtil.matches(rawPassword, user.getPassword())) {
            log.warn("员工登录失败 账号={}", username);
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == Constants.DISABLED) {
            throw new BizException("该账号已被停用，请联系管理员");
        }

        if (PasswordUtil.isLegacy(user.getPassword())) {
            String upgraded = PasswordUtil.encrypt(rawPassword);
            int changed = sysUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId()).eq(SysUser::getPassword, user.getPassword()).set(SysUser::getPassword, upgraded));
            if (changed == 0) throw new BizException("账号信息已变更，请重新登录");
            user.setPassword(upgraded);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setCredentialStamp(PasswordUtil.stamp(user.getPassword()));
        loginUser.setId(user.getId());
        loginUser.setName(user.getRealName());
        loginUser.setAccount(user.getUsername());
        loginUser.setRole(user.getRole());
        log.info("员工登录成功 账号={} 角色={}", username, user.getRole());
        return loginUser;
    }

    /** 会员登录（手机号 + 密码） */
    @org.springframework.transaction.annotation.Transactional
    public LoginUser loginMember(String phone, String rawPassword) {
        if (phone == null || phone.isBlank() || rawPassword == null) {
            throw new BizException("请输入手机号和密码");
        }
        Member member = memberMapper.selectByPhone(phone.trim());

        if (member == null || !PasswordUtil.matches(rawPassword, member.getPassword())) {
            log.warn("会员登录失败 手机号={}", phone);
            throw new BizException("手机号或密码错误");
        }
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，请联系前台");
        }

        if (PasswordUtil.isLegacy(member.getPassword())) {
            String upgraded = PasswordUtil.encrypt(rawPassword);
            int changed = memberMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member>()
                    .eq(Member::getId, member.getId()).eq(Member::getPassword, member.getPassword()).set(Member::getPassword, upgraded));
            if (changed == 0) throw new BizException("账号信息已变更，请重新登录");
            member.setPassword(upgraded);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setCredentialStamp(PasswordUtil.stamp(member.getPassword()));
        loginUser.setId(member.getId());
        loginUser.setName(member.getName());
        loginUser.setAccount(member.getPhone());
        loginUser.setCardNo(member.getCardNo());
        loginUser.setRole(Constants.ROLE_MEMBER);
        log.info("会员登录成功 手机号={} 卡号={}", phone, member.getCardNo());
        return loginUser;
    }

    /** 会员自助注册 */
    public Member register(String phone, String rawPassword, String name) {
        return memberService.register(phone, rawPassword, name);
    }

    /** 修改员工密码 */
    @org.springframework.transaction.annotation.Transactional
    public void changeStaffPassword(Long staffId, String oldRaw, String newRaw) {
        SysUser user = sysUserMapper.selectForUpdate(staffId);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        if (!PasswordUtil.matches(oldRaw, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (newRaw == null || newRaw.length() < 6) {
            throw new BizException("新密码长度不能少于 6 位");
        }
        sysUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId()).eq(SysUser::getPassword, user.getPassword())
                .set(SysUser::getPassword, PasswordUtil.encrypt(newRaw)));
        log.info("员工密码已修改 账号={}", user.getUsername());
    }
}
