package com.cafe.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 营收统计 Mapper。
 * 统计类 SQL 涉及多表聚合与日期分组，统一写在 resources/mapper/StatsMapper.xml 中便于查阅与优化。
 */
@Mapper
public interface StatsMapper {

    /** 某时段的上机收入合计 */
    BigDecimal sumHourFee(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 某时段的商品收入合计 */
    BigDecimal sumProductFee(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 某时段的设备租赁收入合计 */
    BigDecimal sumRentalFee(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 某时段的充值总额 */
    BigDecimal sumRecharge(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 某收银员在某时段经办的现金充值总额。
     *
     * 交接班对账必须按经办人统计：全店充值总额包含线上充值与其他收银员的业绩，
     * 拿它核对本班次现金箱金额是无意义的。
     */
    BigDecimal sumCashRechargeByOperator(@Param("operatorId") Long operatorId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /** 某时段的上机人次 */
    int countSessions(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 从指定日期到今天的收入趋势：日期 / 上机收入 / 商品收入 / 租赁收入 */
    List<Map<String, Object>> revenueTrend(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    /** 各区域收入占比（按上机记录所属机位区域分组） */
    List<Map<String, Object>> areaRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 热销商品排行 TOP N */
    List<Map<String, Object>> topProducts(@Param("limit") int limit, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
