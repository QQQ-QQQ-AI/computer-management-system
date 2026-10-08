package com.cafe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 座位图单元格视图对象。
 *
 * <p>为什么不直接把 Seat 实体丢给页面：座位图需要展示的信息超出了机位表本身，
 * 包括「当前是否处在某个有效预约时段内」「当前上机顾客是谁」「已上机多久」，
 * 这些都来自其他表。若让模板自己去拼凑，Thymeleaf 里会充满跨表判断逻辑，
 * 且前端轮询拿到的 JSON 也会缺少这些字段。因此统一在本对象里组装好。</p>
 *
 * <p><b>关于 displayStatus：</b>它是「机位状态 + 预约状态」派生出来的展示态，
 * 取值可能是 FREE / USING / MAINTENANCE / RESERVED 四种。前三种与 seat.status 一致，
 * RESERVED 是仅用于展示的派生值（机位表里不存在这个状态）。</p>
 */
@Data
public class SeatView {

    /** 机位ID */
    private Long seatId;

    /** 机位编号，如 A01 */
    private String seatNo;

    /** 所属区域 */
    private String area;

    /** 硬件配置描述 */
    private String config;

    /** 机位真实状态：FREE / USING / MAINTENANCE（与 seat.status 一致） */
    private String status;

    /** 展示态：FREE / USING / MAINTENANCE / RESERVED，座位图据此着色 */
    private String displayStatus;

    /** 展示态中文名：空闲 / 使用中 / 维修 / 预约 */
    private String displayStatusName;

    /** 是否可开台（仅 displayStatus = FREE 时为 true） */
    private boolean openable;

    /* ---------- 使用中时的上机信息 ---------- */

    /** 当前上机记录ID */
    private Long sessionId;

    /** 当前上机会员姓名 */
    private String memberName;

    /** 上线时间 */
    private LocalDateTime startTime;

    /** 已上机时长，如「1 时 20 分」 */
    private String elapsed;

    /** 当前应收金额估算 */
    private BigDecimal estimateFee;

    /* ---------- 预约信息 ---------- */

    /** 生效预约单ID */
    private Long reservationId;

    /** 预约人姓名 */
    private String reservationMemberName;

    /** 预约开始时间 */
    private LocalDateTime reservationStart;

    /** 预约结束时间 */
    private LocalDateTime reservationEnd;

    /** 距预约到点还有多久，如「30 分钟后」「已到点」（到点后仍在时段内显示进行中） */
    private String reservationHint;

    /** 悬停提示文案，前端直接用于 title 属性 */
    private String tooltip;
}
