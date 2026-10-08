package com.cafe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置。
 *
 * 目前用于预约超时自动作废（见 ReservationService.expireTimeout）：
 * 顾客预约后未按时到店，超过开始时间15分钟或时段结束后置为已过期，避免无效预约长期占用时段。
 */
@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="cafe.scheduling.enabled", havingValue="true", matchIfMissing=true)
public class SchedulingConfig {
}
