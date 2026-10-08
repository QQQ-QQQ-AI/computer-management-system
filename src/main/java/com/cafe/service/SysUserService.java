package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.PasswordUtil;
import com.cafe.entity.SysUser;
import com.cafe.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户服务：收银员与管理员账号的维护。
 *
 * 账号一律停用而非删除：删除后会丢失其历史业务记录的经办人关联，
 * 导致充值流水、上机记录无法追溯责任人，对账失去依据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;

    public List<SysUser> listAll(String role) {
        return sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                        .orderByAsc(SysUser::getId));
    }

    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("账号不存在，ID=" + id);
        }
        return user;
    }

    /** 新增员工账号；角色仅允许 CASHIER 或 ADMIN */
    @Transactional(rollbackFor = Exception.class)
    public SysUser create(String username, String rawPassword, String realName, String role) {
        if (username == null || username.isBlank()) {
            throw new BizException("登录账号不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        if (!Constants.ROLE_CASHIER.equals(role) && !Constants.ROLE_ADMIN.equals(role)) {
            throw new BizException("角色只能为收银员或管理员");
        }
        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (exists != null && exists > 0) {
            throw new BizException("登录账号「" + username + "」已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encrypt(rawPassword));
        user.setRealName(realName == null || realName.isBlank() ? username.trim() : realName.trim());
        user.setRole(role);
        user.setStatus(Constants.ENABLED);
        sysUserMapper.insert(user);
        log.info("新增员工账号 账号={} 角色={}", username, role);
        return user;
    }

    /** 启用或停用账号 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, int status) {
        SysUser user = sysUserMapper.selectForUpdate(id);
        if (user == null) throw new BizException("账号不存在");
        if (status != Constants.ENABLED && status != Constants.DISABLED) throw new BizException("账号状态无效");
        user.setStatus(status);
        sysUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id).set(SysUser::getRealName, user.getRealName()).set(SysUser::getRole, user.getRole())
                .set(SysUser::getStatus, user.getStatus()).set(SysUser::getPassword, user.getPassword()));
        log.info("员工账号状态变更 账号={} -> {}", user.getUsername(), status);
    }

    /** 修改员工资料（姓名、角色） */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long id, String realName, String role) {
        SysUser user = sysUserMapper.selectForUpdate(id);
        if (user == null) throw new BizException("账号不存在");
        if (realName != null && !realName.isBlank()) {
            user.setRealName(realName.trim());
        }
        if (role != null && !role.isBlank()) {
            if (!Constants.ROLE_CASHIER.equals(role) && !Constants.ROLE_ADMIN.equals(role)) {
                throw new BizException("角色只能为收银员或管理员");
            }
            user.setRole(role);
        }
        sysUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id).set(SysUser::getRealName, user.getRealName()).set(SysUser::getRole, user.getRole())
                .set(SysUser::getStatus, user.getStatus()).set(SysUser::getPassword, user.getPassword()));
    }

    /** 管理员重置员工密码 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newRawPassword) {
        if (newRawPassword == null || newRawPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        SysUser user = sysUserMapper.selectForUpdate(id);
        if (user == null) throw new BizException("账号不存在");
        user.setPassword(PasswordUtil.encrypt(newRawPassword));
        sysUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id).set(SysUser::getRealName, user.getRealName()).set(SysUser::getRole, user.getRole())
                .set(SysUser::getStatus, user.getStatus()).set(SysUser::getPassword, user.getPassword()));
        log.info("员工密码已重置 账号={}", user.getUsername());
    }
}
