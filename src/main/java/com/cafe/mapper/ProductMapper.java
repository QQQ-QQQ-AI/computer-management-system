package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 商品 Mapper */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    com.cafe.entity.Product selectForUpdate(@Param("id") Long id);


    /**
     * 扣减库存。带 stock >= quantity 条件，库存不足时影响行数为 0，
     * 以此在数据库层面兜住并发超卖问题。
     */
    @Update("""
            UPDATE product SET stock = stock - #{quantity}
            WHERE id = #{productId} AND stock >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
