package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.EquipmentRental;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 设备租赁 Mapper */
@Mapper
public interface EquipmentRentalMapper extends BaseMapper<EquipmentRental> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM equipment_rental WHERE id = #{id} FOR UPDATE")
    com.cafe.entity.EquipmentRental selectForUpdate(@Param("id") Long id);


    /** 分页查询租赁记录，联表带出会员与设备信息 */
    IPage<EquipmentRental> selectDetailPage(IPage<EquipmentRental> page,
                                            @Param("memberId") Long memberId,
                                            @Param("status") String status);
}
