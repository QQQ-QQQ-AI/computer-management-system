package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.RechargeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 充值记录 Mapper */
@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {

    /** 分页查询充值流水（联表带出会员姓名、卡号），会员传自己的ID，收银员传 null 查全部 */
    IPage<RechargeRecord> selectDetailPage(IPage<RechargeRecord> page,
                                           @Param("memberId") Long memberId);
}
