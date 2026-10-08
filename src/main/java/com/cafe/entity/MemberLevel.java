package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级配置。
 *
 * 该表让「会员等级」真正参与计费：等级折扣与计费规则中的区域会员折扣
 * 取更优惠的一个作为实际上机折扣，避免等级沦为纯展示字段。
 */
@Data
@TableName("member_level")
public class MemberLevel {

    /** 等级编码，同时作为主键：1 普通 / 2 银卡 / 3 金卡 */
    @TableId(type = IdType.INPUT)
    private Integer level;

    /** 等级名称，可由管理员维护 */
    private String levelName;

    /** 等级折扣，0.95 表示九五折 */
    private BigDecimal discount;

    /** 升级所需累计消费额 */
    private BigDecimal upgradeAmount;

    /** 等级说明 */
    private String description;
}
