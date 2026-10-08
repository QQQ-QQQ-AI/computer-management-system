package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 充值记录 */
@Data
@TableName("recharge_record")
public class RechargeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    /** 充值金额 */
    private BigDecimal amount;

    /** ONLINE 线上自助 / CASH 收银台现金 */
    private String type;

    /** 经办收银员ID，线上充值为空 */
    private Long operatorId;

    private LocalDateTime createTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String cardNo;

    @TableField(exist = false)
    private String typeName;

    public String getTypeName() {
        return "ONLINE".equals(type) ? "线上自助" : "收银台现金";
    }
}
