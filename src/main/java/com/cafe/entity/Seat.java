package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 机位 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 机位编号，如 A01 */
    private String seatNo;

    /** 所属区域 */
    private String area;

    /** 硬件配置描述 */
    private String config;

    /** 状态：FREE / USING / RESERVED / MAINTENANCE */
    private String status;

    private LocalDateTime createTime;
}
