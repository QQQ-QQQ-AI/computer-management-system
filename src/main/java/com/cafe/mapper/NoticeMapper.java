package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

/** 公告 Mapper */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM notice WHERE id=#{id} FOR UPDATE")
    Notice selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
