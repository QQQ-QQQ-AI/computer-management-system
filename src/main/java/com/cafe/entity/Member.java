package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员 */
@Data
@TableName("member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会员卡号 */
    private String cardNo;

    /** 姓名 */
    private String name;

    /** 身份证号 */
    private String idCard;

    /** 手机号（同时作为会员登录账号） */
    private String phone;

    /** 密码摘要（BCrypt，兼容历史MD5） */
    private String password;

    /** 账户余额 */
    private BigDecimal balance;

    /** 冻结押金（设备租赁时冻结） */
    private BigDecimal deposit;

    /** 会员等级：1 普通 / 2 银卡 / 3 金卡 */
    private Integer level;

    /** 1 正常 / 0 冻结 */
    private Integer status;

    private LocalDateTime createTime;

    /* ---------- 联表回显字段，非数据库列 ---------- */

    /**
     * 等级名称。来自 member_level 表而非硬编码：
     * 等级名称与折扣均可由管理员配置，写死在代码里会导致改了配置页面不生效。
     */
    @TableField(exist = false)
    private String levelName;

    /** 该等级对应的折扣，计费时与区域会员折扣取更优者 */
    @TableField(exist = false)
    private BigDecimal levelDiscount;
}
