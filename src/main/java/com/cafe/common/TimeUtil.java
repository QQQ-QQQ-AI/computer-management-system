package com.cafe.common;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 时间展示工具。
 *
 * 把「已上机时长」这类格式化统一放在这里：收银工作台与管理端概览都要显示，
 * 各写一份容易出现两处口径不一致（例如一处按分钟、一处按小时）。
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * 计算从 start 到当前时刻的时长，格式化为「X 时 Y 分」。
     * 不足 1 分钟统一显示「不足 1 分钟」，避免出现「0 时 0 分」这种让人误解为没开始的写法。
     */
    public static String elapsedFrom(LocalDateTime start) {
        if (start == null) {
            return "—";
        }
        long minutes = Duration.between(start, LocalDateTime.now()).toMinutes();
        if (minutes < 1) {
            return "不足 1 分钟";
        }
        return (minutes / 60) + " 时 " + (minutes % 60) + " 分";
    }
}
