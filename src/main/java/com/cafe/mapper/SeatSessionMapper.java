package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.SeatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 上机记录 Mapper（含联表查询，SQL 写在 resources/mapper/SeatSessionMapper.xml） */
@Mapper
public interface SeatSessionMapper extends BaseMapper<SeatSession> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM seat_session WHERE id = #{id} FOR UPDATE")
    SeatSession selectForUpdate(@Param("id") Long id);

    @org.apache.ibatis.annotations.Select("SELECT * FROM seat_session WHERE member_id = #{memberId} AND status = 'USING' LIMIT 1 FOR UPDATE")
    SeatSession selectActiveForUpdate(@Param("memberId") Long memberId);


    /**
     * 分页查询上机记录，并联表带出会员姓名、卡号、机位编号与区域。
     *
     * @param page     分页对象
     * @param memberId 会员ID，为空表示查询全部（管理员/收银员视角）
     * @param status   记录状态，为空表示不限
     */
    IPage<SeatSession> selectDetailPage(IPage<SeatSession> page,
                                        @Param("memberId") Long memberId,
                                        @Param("status") String status);

    /** 查询当前所有「使用中」的记录，用于收银台下机结算列表 */
    List<SeatSession> selectUsingList();

    /**
     * 查询某会员进行中的上机记录，并带出机位编号与区域。
     *
     * 不能用 selectOne 直接查：seatNo、area 是联表回显字段，不联表取到的都是 null，
     * 页面上机位一栏会显示成空白或裸 ID。
     */
    SeatSession selectActiveByMember(@Param("memberId") Long memberId);

    /** 统计某会员在某时段内的上机消费合计 */
    java.math.BigDecimal sumHourFeeByMember(@Param("memberId") Long memberId,
                                            @Param("start") java.time.LocalDateTime start,
                                            @Param("end") java.time.LocalDateTime end);

    /**
     * 会员历史累计上机费（全部已结算记录）。
     * 会员端「上机费用查询」展示的是累计值，不能拿当前页的记录求和充数。
     */
    @org.apache.ibatis.annotations.Select("""
            SELECT IFNULL(SUM(hour_fee), 0) FROM seat_session
            WHERE member_id = #{memberId} AND status = 'FINISHED'
            """)
    java.math.BigDecimal sumHourFeeTotal(@Param("memberId") Long memberId);
}
