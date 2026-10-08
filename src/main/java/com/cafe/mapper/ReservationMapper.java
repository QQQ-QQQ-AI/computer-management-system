package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 预约 Mapper */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("SELECT * FROM reservation WHERE id = #{id} FOR UPDATE")
    Reservation selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM reservation WHERE seat_id = #{seatId} AND status IN ('PENDING','USED') AND start_time <= #{now} AND end_time > #{now} FOR UPDATE")
    java.util.List<Reservation> selectCurrentForUpdate(@Param("seatId") Long seatId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM reservation WHERE seat_id = #{seatId} AND status IN ('PENDING','USED') AND start_time < #{end} AND end_time > #{start} FOR UPDATE")
    java.util.List<Reservation> selectConflictsForUpdate(@Param("seatId") Long seatId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @org.apache.ibatis.annotations.Update("UPDATE reservation SET status='EXPIRED' WHERE status='PENDING' AND (start_time < DATE_SUB(NOW(), INTERVAL 15 MINUTE) OR end_time <= NOW())")
    int expirePending();


    /**
     * 预约时段冲突检测。
     *
     * 判定规则：同一机位、状态为待核销或已核销的预约中，
     * 若存在 [start, end) 与已有预约区间重叠，即视为冲突。
     * 区间重叠条件：newStart < oldEnd AND newEnd > oldStart
     *
     * @param seatId   机位ID
     * @param start    新预约开始时间
     * @param end      新预约结束时间
     * @param excludeId 需排除的预约ID（修改自身时传自身ID，新增时传 null）
     * @return 冲突数量，>0 表示该时段已被占用
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM reservation
            WHERE seat_id = #{seatId}
              AND status IN ('PENDING', 'USED')
              AND #{start} &lt; end_time
              AND #{end} &gt; start_time
              <if test="excludeId != null">
                AND id &lt;&gt; #{excludeId}
              </if>
            </script>
            """)
    int countConflict(@Param("seatId") Long seatId,
                      @Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end,
                      @Param("excludeId") Long excludeId);

    /** 分页查询预约记录（联表带出会员与机位信息） */
    IPage<Reservation> selectDetailPage(IPage<Reservation> page,
                                        @Param("memberId") Long memberId,
                                        @Param("status") String status);

    /**
     * 查询「在指定时刻仍处于有效预约时段内」的全部预约，联表带出会员姓名。
     *
     * 判定条件：状态为待核销或已核销，且 start_time &lt;= moment &lt; end_time。
     * 注意区间是左闭右开：预约到 20:00 结束，则 20:00 整已不属于该预约时段，
     * 与时段冲突检测（newStart &lt; oldEnd AND newEnd &gt; oldStart）保持同一口径，
     * 避免出现「冲突检测认为不重叠、但座位图认为仍在预约中」的矛盾。
     *
     * 供座位图实时判断某机位此刻是否该显示为「预约中」使用。
     *
     * @param moment 判定时刻，通常传当前时间
     * @return 该时刻生效的预约列表（含 seatId、memberName）
     */
    @Select("""
            SELECT r.*, m.name AS member_name
            FROM reservation r
            LEFT JOIN member m ON m.id = r.member_id
            WHERE r.status IN ('PENDING', 'USED')
              AND r.start_time <= #{moment}
              AND r.end_time > #{moment}
            """)
    java.util.List<Reservation> selectOccurringAt(@Param("moment") LocalDateTime moment);

    /**
     * 查询某会员当前有效的预约（待核销且未过期），联表带出机位编号与区域。
     * 同样不能直接用条件构造器查：seatNo、area 为联表字段，不联表取不到。
     */
    java.util.List<Reservation> selectActiveByMember(@Param("memberId") Long memberId);

    /**
     * 查询指定前缀下最大的预约单号，用于生成新单号。
     *
     * 用 MAX 而不是 COUNT 推导序号：COUNT 在有记录被删除、或历史数据编号格式不同时
     * 会算出已被占用的序号，导致唯一约束冲突。此举与会员卡号的生成方式保持一致。
     */
    @Select("SELECT IFNULL(MAX(code), '') FROM reservation WHERE code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCode(@Param("prefix") String prefix);

    /**
     * 将「已过预约到店时间且仍待核销」的预约批量置为已过期。
     * 由定时任务调用，用于超时未到店自动取消并释放机位。
     */
    @Select("""
            SELECT * FROM reservation
            WHERE status = 'PENDING' AND (start_time < DATE_SUB(NOW(), INTERVAL 15 MINUTE) OR end_time <= NOW())
            """)
    java.util.List<Reservation> selectTimeoutList();
}
