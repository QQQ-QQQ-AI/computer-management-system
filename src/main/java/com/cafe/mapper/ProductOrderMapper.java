package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.ProductOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/** 商品消费记账 Mapper */
@Mapper
public interface ProductOrderMapper extends BaseMapper<ProductOrder> {

    @org.apache.ibatis.annotations.Select("SELECT amount FROM product_order WHERE session_id=#{sessionId} FOR UPDATE")
    java.util.List<BigDecimal> selectAmountsForUpdate(@Param("sessionId") Long sessionId);

    /** 分页查询商品消费明细 */
    IPage<ProductOrder> selectDetailPage(IPage<ProductOrder> page,
                                         @Param("memberId") Long memberId,
                                         @Param("sessionId") Long sessionId);

    /** 汇总某条上机记录下已记账的商品金额（下机结算时使用） */
    BigDecimal sumBySessionId(@Param("sessionId") Long sessionId);

    /** 会员历史累计商品消费（含当场结清与随上机结算两种情形） */
    @org.apache.ibatis.annotations.Select("""
            SELECT IFNULL(SUM(amount), 0) FROM product_order WHERE member_id = #{memberId}
            """)
    BigDecimal sumAmountByMember(@Param("memberId") Long memberId);
}
