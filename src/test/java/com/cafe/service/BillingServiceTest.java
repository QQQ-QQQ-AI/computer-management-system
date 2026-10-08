package com.cafe.service;

import com.cafe.dto.BillingResult;
import com.cafe.entity.BillingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计费规则单元测试。
 *
 * 本类覆盖系统计费的三条核心规则，是「系统测试」章节中计费部分的验证依据：
 *   1. 计费时长向上取整到 5 分钟；
 *   2. 折扣取区域会员折扣与等级折扣中更优惠的一个；
 *   3. 按小时与包时套餐分别计算后自动取优。
 *
 * 期望值与 sql/gen_seed.py 生成的演示数据保持一致：
 * 若某天改动计费规则，这里的用例与种子数据会同时失败，
 * 从而避免「页面显示的费用与统计出来的收入对不上」。
 *
 * 计费为纯计算，不依赖数据库，因此不需要启动 Spring 容器。
 */
class BillingServiceTest {

    private final BillingService billingService = new BillingService();

    /** 普通区：6 元/小时，5 小时 25 元套餐，区域会员折扣 1.00 */
    private BillingRule normalArea() {
        return rule("普通区", "6.00", 5, "25.00", "1.00");
    }

    /** 竞技区：10 元/小时，5 小时 45 元套餐，区域会员折扣 0.95 */
    private BillingRule arenaArea() {
        return rule("竞技区", "10.00", 5, "45.00", "0.95");
    }

    /** 包厢区：15 元/小时，无套餐，区域会员折扣 1.00 */
    private BillingRule boxArea() {
        return rule("包厢区", "15.00", null, null, "1.00");
    }

    private BillingRule rule(String area, String hour, Integer pkgHours,
                             String pkgPrice, String discount) {
        BillingRule r = new BillingRule();
        r.setArea(area);
        r.setHourPrice(new BigDecimal(hour));
        r.setPackageHours(pkgHours);
        r.setPackagePrice(pkgPrice == null ? null : new BigDecimal(pkgPrice));
        r.setMemberDiscount(new BigDecimal(discount));
        return r;
    }

    /* ==================== 规则一：向上取整到 5 分钟 ==================== */

    @Test
    @DisplayName("计费时长向上取整到 5 分钟：1 分钟按 5 分钟计")
    void shouldRoundUpToFiveMinutes() {
        BillingResult r = billingService.calculate(1, normalArea(), new BigDecimal("1.00"));
        assertEquals(5, r.getBillableMinutes());
        // 6 元/小时 × 5 分钟 = 0.50 元
        assertEquals(new BigDecimal("0.50"), r.getFinalFee());
    }

    @Test
    @DisplayName("计费时长向上取整到 5 分钟：61 分钟按 65 分钟计")
    void shouldRoundUpToSixtyFiveMinutes() {
        BillingResult r = billingService.calculate(61, normalArea(), new BigDecimal("1.00"));
        assertEquals(65, r.getBillableMinutes());
        // 6 × 65 / 60 = 6.50
        assertEquals(new BigDecimal("6.50"), r.getFinalFee());
    }

    @Test
    @DisplayName("整数小时不产生多余取整")
    void shouldNotRoundExactHours() {
        BillingResult r = billingService.calculate(180, normalArea(), new BigDecimal("1.00"));
        assertEquals(180, r.getBillableMinutes());
        assertEquals(new BigDecimal("18.00"), r.getFinalFee());
    }

    /* ==================== 规则二：折扣取更优惠者 ==================== */

    @Test
    @DisplayName("等级折扣更优惠时取等级折扣")
    void shouldUseBetterLevelDiscount() {
        // 区域折扣 1.00，金卡等级折扣 0.90 → 取 0.90
        BillingResult r = billingService.calculate(60, normalArea(), new BigDecimal("0.90"));
        assertEquals(0, new BigDecimal("0.90").compareTo(r.getDiscount()));
        assertEquals(new BigDecimal("5.40"), r.getFinalFee());   // 6 × 0.9 × 1
    }

    @Test
    @DisplayName("区域折扣更优惠时取区域折扣")
    void shouldUseBetterAreaDiscount() {
        // 竞技区区域折扣 0.95，普通等级折扣 1.00 → 取 0.95
        BillingResult r = billingService.calculate(60, arenaArea(), new BigDecimal("1.00"));
        assertEquals(0, new BigDecimal("0.95").compareTo(r.getDiscount()));
        assertEquals(new BigDecimal("9.50"), r.getFinalFee());   // 10 × 0.95 × 1
    }

    @Test
    @DisplayName("两个折扣相同且为更优惠者时正常生效")
    void shouldPickSameDiscount() {
        // 竞技区 0.95 与银卡 0.95 → 取 0.95
        BillingResult r = billingService.calculate(60, arenaArea(), new BigDecimal("0.95"));
        assertEquals(0, new BigDecimal("0.95").compareTo(r.getDiscount()));
    }

    /* ==================== 规则三：包时套餐自动取优 ==================== */

    @Test
    @DisplayName("满 5 小时竞技区命中套餐：42.75 优于按小时的 47.50")
    void shouldHitPackageForFiveHoursInArena() {
        // 会员2 为银卡，等级折扣 0.95；竞技区区域折扣 0.95 → 实际折扣 0.95
        BillingResult r = billingService.calculate(300, arenaArea(), new BigDecimal("0.95"));
        assertEquals(new BigDecimal("47.50"), r.getHourlyTotal());   // 10 × 0.95 × 5
        assertEquals(new BigDecimal("42.75"), r.getPackageTotal());  // 45 × 0.95
        assertEquals(new BigDecimal("42.75"), r.getFinalFee());
        assertTrue(r.isHitPackage());
    }

    @Test
    @DisplayName("7 小时竞技区命中套餐：套餐 + 超出部分按小时计")
    void shouldHitPackagePlusExtraHours() {
        // 420 分钟 = 1 份 5 小时套餐 + 2 小时零头
        BillingResult r = billingService.calculate(420, arenaArea(), new BigDecimal("1.00"));
        // 按小时：10 × 0.95 × 7 = 66.50
        assertEquals(new BigDecimal("66.50"), r.getHourlyTotal());
        // 套餐：45 × 0.95 + 10 × 0.95 × 2 = 42.75 + 19.00 = 61.75
        assertEquals(new BigDecimal("61.75"), r.getPackageTotal());
        assertEquals(new BigDecimal("61.75"), r.getFinalFee());
        assertTrue(r.isHitPackage());
    }

    @Test
    @DisplayName("时长不足套餐时按小时更划算，不应误选套餐")
    void shouldPreferHourlyWhenPackageIsWorse() {
        // 3 小时普通区：按小时 18.00，套餐 25.00 → 应选按小时
        BillingResult r = billingService.calculate(180, normalArea(), new BigDecimal("1.00"));
        assertEquals(new BigDecimal("18.00"), r.getFinalFee());
        assertFalse(r.isHitPackage());
    }

    @Test
    @DisplayName("未配置套餐的区域只按小时计费")
    void shouldCalculateHourlyWhenNoPackage() {
        BillingResult r = billingService.calculate(360, boxArea(), new BigDecimal("1.00"));
        assertEquals(new BigDecimal("90.00"), r.getFinalFee());  // 15 × 6
        assertFalse(r.isHitPackage());
    }

    /* ==================== 边界情况 ==================== */

    @Test
    @DisplayName("零分钟上机不产生费用")
    void shouldChargeZeroForZeroMinutes() {
        BillingResult r = billingService.calculate(0, normalArea(), new BigDecimal("1.00"));
        assertEquals(0, r.getBillableMinutes());
        assertEquals(new BigDecimal("0.00"), r.getFinalFee());
    }

    @Test
    @DisplayName("等级折扣为空时按不打折处理，不应抛异常")
    void shouldHandleNullLevelDiscount() {
        BillingResult r = billingService.calculate(60, normalArea(), (BigDecimal) null);
        assertEquals(0, BigDecimal.ONE.compareTo(r.getDiscount()));
        assertEquals(new BigDecimal("6.00"), r.getFinalFee());
    }

    @Test
    @DisplayName("计费说明应包含关键金额，便于收银台向顾客解释")
    void shouldProduceReadableExplain() {
        BillingResult r = billingService.calculate(300, arenaArea(), new BigDecimal("0.95"));
        String explain = r.getExplain();
        assertTrue(explain.contains("竞技区"), "说明中应包含区域");
        assertTrue(explain.contains("42.75"), "说明中应包含最终金额");
        assertTrue(explain.contains("包时"), "命中套餐时应说明");
    }
}
