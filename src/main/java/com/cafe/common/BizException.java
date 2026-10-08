package com.cafe.common;

/**
 * 业务异常。
 * 用于表达「可预期的业务失败」，例如余额不足、机位已被占用、非法状态切换等。
 * 由全局异常处理器统一转换为 Result 返回，不打印堆栈。
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
