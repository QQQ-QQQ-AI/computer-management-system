package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.MemberLevel;
import org.apache.ibatis.annotations.Mapper;

/** 会员等级配置 Mapper */
@Mapper
public interface MemberLevelMapper extends BaseMapper<MemberLevel> {
}
