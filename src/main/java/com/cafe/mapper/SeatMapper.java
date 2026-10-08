package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 机位 Mapper */
@Mapper
public interface SeatMapper extends BaseMapper<Seat> {
    @Select("SELECT * FROM seat WHERE id = #{id} FOR UPDATE")
    Seat selectForUpdate(@Param("id") Long id);


    /**
     * 查询各区域的机位总数与空闲数，用于机位管理页顶部的概览卡片。
     */
    @Select("""
            SELECT area,
                   COUNT(*)                                  AS total,
                   SUM(CASE WHEN status = 'FREE' THEN 1 ELSE 0 END) AS freeCount,
                   SUM(CASE WHEN status = 'USING' THEN 1 ELSE 0 END) AS usingCount
            FROM seat
            GROUP BY area
            ORDER BY area
            """)
    List<java.util.Map<String, Object>> countByArea();

    /**
     * 机位状态 CAS 更新（Compare-And-Swap）—— 并发安全的开台/结算关键操作。
     *
     * 为什么不能「先 SELECT 判断状态，再 UPDATE」：
     * 两个收银员同时给同一机位开台时，两边都查到 FREE，随后都执行更新，结果一台机器被开两次。
     * 把状态判断下推到 UPDATE 的 WHERE 条件里，由数据库保证「判断 + 修改」是一个原子操作：
     *   返回 1 → 抢占成功；
     *   返回 0 → 状态已被他人改变（含机位不存在），调用方应抛出业务异常。
     *
     * @param seatId       机位ID
     * @param expectStatus 期望的原状态，如 FREE
     * @param targetStatus 要改成的目标状态，如 USING
     * @return 受影响行数，1 表示成功抢占
     */
    @Update("""
            UPDATE seat SET status = #{targetStatus}
            WHERE id = #{seatId} AND status = #{expectStatus}
            """)
    int casStatus(@Param("seatId") Long seatId,
                  @Param("expectStatus") String expectStatus,
                  @Param("targetStatus") String targetStatus);
}
