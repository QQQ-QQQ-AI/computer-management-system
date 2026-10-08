package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.Equipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 设备 Mapper */
@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM equipment WHERE id = #{id} FOR UPDATE")
    Equipment selectForUpdate(@Param("id") Long id);


    /**
     * 设备状态 CAS 更新，作用与 SeatMapper.casStatus 相同。
     * 用于防止「同一外设被两名收银员同时租给不同会员」的并发问题。
     *
     * @return 受影响行数，1 表示抢占成功
     */
    @Update("""
            UPDATE equipment SET status = #{targetStatus}
            WHERE id = #{equipmentId} AND status = #{expectStatus}
            """)
    int casStatus(@Param("equipmentId") Long equipmentId,
                  @Param("expectStatus") String expectStatus,
                  @Param("targetStatus") String targetStatus);
}
