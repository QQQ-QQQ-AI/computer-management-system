package com.cafe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计费结果。
 *
 * 保留「按小时」与「包时套餐」两个方案的金额，而不只返回最终值，
 * 目的是让收银台能向顾客展示「为什么收这个价」，
 * 也便于系统测试章节验证自动取优逻辑是否正确。
 */
@Data
public class BillingResult {

    /** 原始上机分钟数 */
    private int rawMinutes;

    /** 计费时长：向上取整到 5 分钟后的分钟数 */
    private int billableMinutes;

    /** 实际生效的折扣 = min(区域会员折扣, 等级折扣) */
    private BigDecimal discount;

    /** 按小时方案的金额 */
    private BigDecimal hourlyTotal;

    /** 包时套餐方案的金额，未配置套餐时为 null */
    private BigDecimal packageTotal;

    /** 最终计费金额（两方案取低者） */
    private BigDecimal finalFee;

    /** 是否命中包时套餐 */
    private boolean hitPackage;

    /** 计费过程说明，用于页面展示与日志追溯 */
    private String explain;
}
