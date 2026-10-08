package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户流水。
 *
 * 会员余额的每一次变动都必须写入一条流水，且记录变动前后余额。
 * 这样「当前余额」始终可由流水逐笔累加重算校验：
 *      SUM(change_amount) == member.balance
 * 这是验证结算数据一致性的直接依据，也是发现账务异常的手段。
 */
@Data
@TableName("balance_record")
public class BalanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    /** 变动类型：RECHARGE / HOUR_FEE / PRODUCT / RENT_FEE / DEPOSIT_FREEZE / DEPOSIT_UNFREEZE */
    private String changeType;

    /** 变动金额，正数表示余额增加，负数表示减少 */
    private BigDecimal changeAmount;

    /** 变动前余额 */
    private BigDecimal beforeBalance;

    /** 变动后余额 */
    private BigDecimal afterBalance;

    /** 关联业务类型：SESSION / PRODUCT_ORDER / RENTAL */
    private String bizType;

    /** 关联业务主键 */
    private Long bizId;

    private String remark;

    /** 经办员工ID，会员自助操作时为空 */
    private Long operatorId;

    private LocalDateTime createTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String cardNo;

    /** 变动类型中文名，用于页面展示 */
    public String getChangeTypeName() {
        if (changeType == null) {
            return "";
        }
        return switch (changeType) {
            case "RECHARGE" -> "充值";
            case "HOUR_FEE" -> "上机费";
            case "PRODUCT" -> "商品消费";
            case "RENT_FEE" -> "设备租金";
            case "DEPOSIT_FREEZE" -> "押金冻结";
            case "DEPOSIT_UNFREEZE" -> "押金解冻";
            default -> changeType;
        };
    }
}
