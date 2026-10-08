package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统用户（管理员 / 收银员） */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码摘要（BCrypt，兼容历史MD5） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：ADMIN / CASHIER */
    private String role;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createTime;
}
