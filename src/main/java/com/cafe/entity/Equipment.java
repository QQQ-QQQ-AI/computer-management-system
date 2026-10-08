package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备（可租赁外设） */
@Data
@TableName("equipment")
public class Equipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号 */
    private String code;

    /** 设备类别 */
    private String category;

    /** 日租金 */
    private BigDecimal rentPrice;

    /** 押金 */
    private BigDecimal deposit;

    /** IN_STOCK 在库 / RENTED 已租出 / REPAIR 维修中 */
    private String status;

    private LocalDateTime createTime;
}
