package com.cafe.service;

import com.cafe.dto.BillingResult;
import com.cafe.entity.BillingRule;
import com.cafe.entity.Member;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计费服务 —— 系统的核心业务规则所在。
 *
 * 三条规则（与 sql/gen_seed.py 中的实现必须保持一致，否则演示数据与运行时算出的费用会对不上）：
 *
 * 1. 计费时长向上取整到 5 分钟。
 *    不满 5 分钟按 5 分钟计，避免秒级上机也走一次完整计费流程，
 *    同时让收银台的计价结果可预期、易解释。
 *
 * 2. 折扣取「区域会员折扣」与「会员等级折扣」中更优惠的一个，即取较小值。
 *    不采用两者相乘：相乘会算出 0.855 这类难以向顾客解释的数字，
 *    而「区域体现资源成本差异、等级体现会员权益，取更优的一档」语义清晰。
 *
 * 3. 按小时方案与包时套餐方案分别计算，最终取金额更低者（自动取优）。
 *    顾客不需要自己判断哪个划算，系统承担这个判断。
 *    包时套餐可叠加出现：超出部分按套餐计，余下零头仍按小时计。
 *
 * 本类为纯计算，不读写数据库，便于单元测试。
 */
@Service
public class BillingService {

    /** 计费时长取整粒度（分钟） */
    private static final int ROUND_UNIT_MINUTES = 5;

    /** 中间计算精度，最终结果再四舍五入到 2 位，避免中间步骤丢精度 */
    private static final int CALC_SCALE = 10;

    /**
     * 计算上机费用。
     *
     * @param minutes       实际上机分钟数
     * @param rule          机位所在区域的计费规则
     * @param levelDiscount 会员等级折扣，可为 null（非会员或未取到等级时按 1.00 处理）
     */
    public BillingResult calculate(int minutes, BillingRule rule, BigDecimal levelDiscount) {
        if (rule == null) {
            throw new IllegalArgumentException("缺少计费规则，无法计算上机费用");
        }
        if (minutes < 0) {
            minutes = 0;
        }

        BillingResult r = new BillingResult();
        r.setRawMinutes(minutes);

        // ---- 规则 2：确定实际折扣 ----
        BigDecimal areaDiscount = rule.getMemberDiscount() == null
                ? BigDecimal.ONE : rule.getMemberDiscount();
        BigDecimal lvDiscount = (levelDiscount == null) ? BigDecimal.ONE : levelDiscount;
        BigDecimal discount = areaDiscount.min(lvDiscount);
        r.setDiscount(discount);

        // ---- 规则 1：计费时长向上取整到 5 分钟 ----
        int billable = (int) (Math.ceil(minutes / (double) ROUND_UNIT_MINUTES) * ROUND_UNIT_MINUTES);
        r.setBillableMinutes(billable);

        // ---- 按小时方案 ----
        BigDecimal hourlyTotal = rule.getHourPrice()
                .multiply(discount)
                .multiply(BigDecimal.valueOf(billable))
                .divide(BigDecimal.valueOf(60), CALC_SCALE, RoundingMode.HALF_UP);

        // ---- 包时套餐方案（未配置则为空） ----
        BigDecimal packageTotal = null;
        if (rule.getPackageHours() != null && rule.getPackagePrice() != null
                && rule.getPackageHours() > 0) {
            int pkgMinutes = rule.getPackageHours() * 60;
            int whole = billable / pkgMinutes;          // 完整的套餐份数
            int rest = billable % pkgMinutes;           // 超出套餐的零头，仍按小时计
            packageTotal = rule.getPackagePrice()
                    .multiply(discount)
                    .multiply(BigDecimal.valueOf(whole))
                    .add(rule.getHourPrice()
                            .multiply(discount)
                            .multiply(BigDecimal.valueOf(rest))
                            .divide(BigDecimal.valueOf(60), CALC_SCALE, RoundingMode.HALF_UP));
        }

        // ---- 规则 3：两方案取优 ----
        BigDecimal finalFee = hourlyTotal;
        boolean hitPackage = false;
        if (packageTotal != null && packageTotal.compareTo(hourlyTotal) < 0) {
            finalFee = packageTotal;
            hitPackage = true;
        }

        r.setHourlyTotal(scale2(hourlyTotal));
        r.setPackageTotal(packageTotal == null ? null : scale2(packageTotal));
        r.setFinalFee(scale2(finalFee));
        r.setHitPackage(hitPackage);
        r.setExplain(buildExplain(rule, discount, minutes, billable, r, hitPackage));
        return r;
    }

    /** 便捷重载：直接传入会员，自动取其等级折扣 */
    public BillingResult calculate(int minutes, BillingRule rule, Member member) {
        BigDecimal levelDiscount = (member == null) ? BigDecimal.ONE : member.getLevelDiscount();
        return calculate(minutes, rule, levelDiscount);
    }

    private BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 生成人类可读的计费说明，收银台可直接展示 */
    private String buildExplain(BillingRule rule, BigDecimal discount, int raw, int billable,
                                BillingResult r, boolean hitPackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("区域：").append(rule.getArea())
                .append("；单价 ").append(rule.getHourPrice()).append(" 元/小时")
                .append("；折扣 ").append(discount);
        if (raw != billable) {
            sb.append("；时长 ").append(raw).append(" 分钟，按 ")
                    .append(billable).append(" 分钟计");
        } else {
            sb.append("；时长 ").append(billable).append(" 分钟");
        }
        sb.append("；按小时计 ").append(r.getHourlyTotal()).append(" 元");
        if (r.getPackageTotal() != null) {
            sb.append("，包时套餐计 ").append(r.getPackageTotal()).append(" 元");
        }
        sb.append("；取优后应收 ").append(r.getFinalFee()).append(" 元");
        if (hitPackage) {
            sb.append("（已自动选用更优惠的包时套餐）");
        }
        return sb.toString();
    }
}
