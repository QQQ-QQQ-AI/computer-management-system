package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 计费规则（按区域配置小时单价与包时套餐） */
@Data
@TableName("billing_rule")
public class BillingRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 区域名称，与机位的 area 对应 */
    private String area;

    /** 小时单价（元/小时） */
    private BigDecimal hourPrice;

    /** 包时套餐时长（小时），可为空 */
    private Integer packageHours;

    /** 包时套餐价格（元），可为空 */
    private BigDecimal packagePrice;

    /** 会员折扣，0.90 表示九折 */
    private BigDecimal memberDiscount;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createTime;
}
