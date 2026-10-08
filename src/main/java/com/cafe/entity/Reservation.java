package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 机位预约 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约单号 */
    private String code;

    private Long memberId;

    private Long seatId;

    /** 预约到店时间 */
    private LocalDateTime startTime;

    /** 预约结束时间 */
    private LocalDateTime endTime;

    /** PENDING 待核销 / USED 已核销 / CANCELED 已取消 / EXPIRED 已过期 */
    private String status;

    private LocalDateTime createTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String memberName;

    @TableField(exist = false)
    private String phone;

    @TableField(exist = false)
    private String seatNo;

    @TableField(exist = false)
    private String area;

    public String getStatusName() {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PENDING" -> "待核销";
            case "USED" -> "已核销";
            case "CANCELED" -> "已取消";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }
}
