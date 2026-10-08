package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cafe.entity.BillingRule;
import org.apache.ibatis.annotations.Mapper;

/** 计费规则 Mapper */
@Mapper
public interface BillingRuleMapper extends BaseMapper<BillingRule> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM billing_rule WHERE id=#{id} FOR UPDATE")
    BillingRule selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
