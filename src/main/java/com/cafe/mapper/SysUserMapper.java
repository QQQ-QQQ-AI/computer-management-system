package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 系统用户 Mapper */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    com.cafe.entity.SysUser selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

}
