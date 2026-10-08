package com.cafe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.entity.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员 Mapper。
 * 单表增删改查由 MyBatis-Plus BaseMapper 提供；
 * 需要带出等级名称与折扣的查询走 XML 联表。
 */
@Mapper
public interface MemberMapper extends BaseMapper<Member> {

    /**
     * 分页查询会员。
     *
     * @param keyword 模糊匹配 姓名 / 卡号 / 手机号，为空表示不限
     * @param status  1 正常 / 0 冻结，为空表示不限
     */
    IPage<Member> selectDetailPage(IPage<Member> page,
                                   @Param("keyword") String keyword,
                                   @Param("status") Integer status);

    /**
     * 按ID查询会员并带出等级名称与折扣。
     * 计费时必须用这个版本，普通 selectById 拿不到 level_discount。
     */
    Member selectWithLevelById(@Param("id") Long id);

    /** 按手机号查询（会员登录用） */
    Member selectByPhone(@Param("phone") String phone);

    /** 按卡号查询（收银台凭卡办理业务用） */
    Member selectByCardNo(@Param("cardNo") String cardNo);

    /**
     * 加行锁读取会员，用于资金操作。
     *
     * 为什么资金操作必须先加锁：账户流水中要记录「变动前余额」与「变动后余额」，
     * 这两个值必须与本次变动严格对应。若不加锁，两个并发请求可能读到同一个旧余额，
     * 各自算出不同的新余额，最终出现「流水前后余额不衔接」与「余额丢失更新」。
     * SELECT ... FOR UPDATE 会把该会员行锁到事务提交为止，把并发操作串行化。
     *
     * 注意：必须在已开启的事务中调用，否则语句执行完锁即释放，起不到保护作用。
     */
    @Select("SELECT * FROM member WHERE id = #{id} FOR UPDATE")
    Member selectForUpdate(@Param("id") Long id);

    /**
     * 查询指定前缀下最大的卡号，用于生成新卡号。
     * 卡号格式：M + 年份 + 4 位流水，如 M20260001。
     */
    @Select("SELECT IFNULL(MAX(card_no), '') FROM member WHERE card_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCardNo(@Param("prefix") String prefix);
}
