package com.cafe.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录态载体，存放于 HttpSession 中。
 * 会员与员工（收银员/管理员）共用同一结构，靠 role 区分。
 */
@Data
public class LoginUser implements Serializable {

    /** 会员ID 或 系统用户ID */
    private Long id;

    /** 姓名 / 会员昵称 */
    private String name;

    /** 登录账号（员工为 username，会员为手机号） */
    private String account;

    /** 角色：MEMBER / CASHIER / ADMIN */
    private String role;

    /** 会员专属：卡号 */
    private String cardNo;

    /** Server-side credential stamp used to revoke old sessions. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String credentialStamp;
}
