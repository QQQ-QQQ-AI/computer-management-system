package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备租赁记录 */
@Data
@TableName("equipment_rental")
public class EquipmentRental {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private Long equipmentId;

    /** 租借时间 */
    private LocalDateTime rentTime;

    /** 归还时间 */
    private LocalDateTime returnTime;

    /** 实际租金（归还时按租期结算） */
    private BigDecimal rentFee;

    /** 冻结押金 */
    private BigDecimal deposit;

    /** RENTING 租借中 / RETURNED 已归还 */
    private String status;

    private Long operatorId;

    private LocalDateTime createTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String cardNo;

    @TableField(exist = false)
    private String equipmentCode;

    @TableField(exist = false)
    private String category;
}
