package com.cafe.service;

import com.cafe.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营收统计服务。
 *
 * 全系统统一口径（这是各项数字能相互对上的前提）：
 *
 *   上机收入 —— 以结算时间 end_time 确认
 *   商品收入 —— 挂账的以所属上机记录的结算时间为准，前台直购的以记账时间为准
 *   租赁收入 —— 以归还时间 return_time 确认，因为租金在归还时才确定租期
 *   充值金额 —— 单独统计，属于预收款，不计入营业收入
 *
 * 若各处口径不一致，会出现「今日上机收入 + 商品收入 ≠ 今日总收入」
 * 这类自相矛盾的结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;

    /** 某日的时间区间 [当天 00:00, 次日 00:00) */
    private LocalDateTime[] rangeOf(LocalDate date) {
        return new LocalDateTime[]{date.atStartOfDay(), date.plusDays(1).atStartOfDay()};
    }

    /**
     * 今日营收概览：上机收入、商品收入、租赁收入、营业收入合计、
     * 上机人次、充值金额。
     */
    public Map<String, Object> todaySummary() {
        LocalDateTime[] r = rangeOf(LocalDate.now());
        BigDecimal hourFee = nz(statsMapper.sumHourFee(r[0], r[1]));
        BigDecimal productFee = nz(statsMapper.sumProductFee(r[0], r[1]));
        BigDecimal rentalFee = nz(statsMapper.sumRentalFee(r[0], r[1]));
        BigDecimal recharge = nz(statsMapper.sumRecharge(r[0], r[1]));
        int sessionCount = statsMapper.countSessions(r[0], r[1]);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", LocalDate.now().toString());
        map.put("hourFee", hourFee);
        map.put("productFee", productFee);
        map.put("rentalFee", rentalFee);
        // 营业收入不含充值：充值是预收款，顾客尚未消费，不能算作收入
        map.put("totalRevenue", hourFee.add(productFee).add(rentalFee));
        map.put("sessionCount", sessionCount);
        map.put("rechargeAmount", recharge);
        return map;
    }

    /**
     * 某收银员今日经办的现金充值总额，用于交接班核对现金箱。
     * 与全店充值总额是两回事：后者含线上充值和其他收银员的业绩。
     */
    public BigDecimal todayCashRechargeBy(Long operatorId) {
        if (operatorId == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime[] r = rangeOf(LocalDate.now());
        return nz(statsMapper.sumCashRechargeByOperator(operatorId, r[0], r[1]));
    }

    /** 指定日期区间的营收汇总，用于统计报表的区间查询 */
    public Map<String, Object> summary(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        BigDecimal hourFee = nz(statsMapper.sumHourFee(start, end));
        BigDecimal productFee = nz(statsMapper.sumProductFee(start, end));
        BigDecimal rentalFee = nz(statsMapper.sumRentalFee(start, end));
        int sessionCount = statsMapper.countSessions(start, end);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("startDate", startDate.toString());
        map.put("endDate", endDate.toString());
        map.put("hourFee", hourFee);
        map.put("productFee", productFee);
        map.put("rentalFee", rentalFee);
        map.put("totalRevenue", hourFee.add(productFee).add(rentalFee));
        map.put("sessionCount", sessionCount);
        map.put("rechargeAmount", nz(statsMapper.sumRecharge(start, end)));
        map.put("areaRevenue", statsMapper.areaRevenue(start, end));
        map.put("topProducts", statsMapper.topProducts(5, start, end));
        return map;
    }

    /**
     * 近 N 天收入趋势。用递归 CTE 补齐日期，没有流水的日期也会返回 0，
     * 避免折线图出现断点。
     */
    public List<Map<String, Object>> trend(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        return trend(LocalDate.now().minusDays(safeDays - 1L), LocalDate.now());
    }

    /** 各区域收入占比（饼图数据源） */
    public List<Map<String, Object>> areaRevenue(LocalDate startDate, LocalDate endDate) {
        return statsMapper.areaRevenue(startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());
    }

    /** 热销商品排行 */
    public List<Map<String, Object>> topProducts(int limit) {
        return topProducts(limit, LocalDate.now().minusDays(6), LocalDate.now());
    }

    public List<Map<String,Object>> trend(LocalDate start, LocalDate end) {
        if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start,end) > 365) throw new com.cafe.common.BizException("统计日期区间必须有效且不超过366天");
        return statsMapper.revenueTrend(start, end);
    }
    public List<Map<String,Object>> topProducts(int limit, LocalDate start, LocalDate end) {
        return statsMapper.topProducts(Math.max(1, Math.min(limit,20)), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
