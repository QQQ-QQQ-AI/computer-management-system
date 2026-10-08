package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.BalanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/** 账户流水 Mapper */
@Mapper
public interface BalanceRecordMapper extends BaseMapper<BalanceRecord> {

    /** 分页查询账户流水，联表带出会员姓名与卡号 */
    IPage<BalanceRecord> selectDetailPage(IPage<BalanceRecord> page,
                                          @Param("memberId") Long memberId,
                                          @Param("changeType") String changeType);

    /**
     * 对账查询：按流水累加重算余额。
     * 与 member.balance 比对即可验证账务是否一致，差额非 0 说明存在数据异常。
     */
    @Select("""
            SELECT IFNULL(SUM(change_amount), 0) FROM balance_record WHERE member_id = #{memberId}
            """)
    BigDecimal sumByMember(@Param("memberId") Long memberId);
}
