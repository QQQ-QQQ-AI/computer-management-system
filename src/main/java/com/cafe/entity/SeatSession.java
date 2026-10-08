package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 上机记录 */
@Data
@TableName("seat_session")
public class SeatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private Long seatId;

    /** 开台时间 */
    private LocalDateTime startTime;

    /** 下机时间 */
    private LocalDateTime endTime;

    /** 上机时长（分钟） */
    private Integer durationMinutes;

    /** 上机费用 */
    private BigDecimal hourFee;

    /** 商品消费金额 */
    private BigDecimal productFee;

    /** 结算总金额 */
    private BigDecimal totalFee;

    /** USING 使用中 / FINISHED 已结算 */
    private String status;

    private LocalDateTime createTime;

    /* ---------- 以下为联表查询回显字段，非数据库列 ---------- */

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String cardNo;

    @TableField(exist = false)
    private String seatNo;

    @TableField(exist = false)
    private String area;
}
