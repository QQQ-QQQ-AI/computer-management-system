package com.cafe.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * 全局异常处理。
 *
 * 分三类处理，关键点是「不把 HTTP 语义错误误报成系统故障」：
 * 1. 404 找不到资源：属于正常的访问结果，必须仍然返回 404；
 * 2. 405 请求方法不支持：返回 405；
 * 3. 业务异常及其它异常：返回友好提示。
 *
 * 若不加区分地用 Exception 兜底，会把 404 变成 500，
 * 既掩盖真实原因，也会让浏览器断链判断、爬虫重试策略等出错。
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 404：地址不存在。不打印堆栈，这是正常访问结果而非系统错误 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException e,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        log.debug("地址不存在 [{}]", request.getRequestURI());
        return build(404, "找不到该页面：" + request.getRequestURI(), request, response);
    }

    /** 405：请求方式不被支持，例如对只接受 POST 的接口发了 GET */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                           HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        log.warn("请求方式不支持 [{}] {}", request.getRequestURI(), e.getMessage());
        return build(405, "该请求方式不被支持：" + e.getMethod(), request, response);
    }

    /** 业务异常：由 Service 层主动抛出，属于「可预期失败」，不打印堆栈 */
    @ExceptionHandler(BizException.class)
    public Object handleBiz(BizException e,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        log.warn("业务异常 [{}] {}", request.getRequestURI(), e.getMessage());
        return build(e.getCode(), e.getMessage(), request, response);
    }

    /** 兜底异常：真正的系统故障，记录完整堆栈便于排查 */
    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        log.error("系统异常 [{}]", request.getRequestURI(), e);
        return build(500, "系统繁忙，请稍后重试：" + e.getMessage(), request, response);
    }

    /**
     * 页面请求渲染错误页；接口请求直接写出 JSON。
     *
     * 为什么不用简单办法返回 Result 对象：本类是 @ControllerAdvice 而非 @RestControllerAdvice，
     * 返回的普通对象会被当成视图名解析；而给方法加 @ResponseBody 又会让 ModelAndView 也被序列化成 JSON。
     * 同一个方法要同时支持两种返回形式，直接操作响应流最明确。
     *
     * @return 页面请求返回 ModelAndView；JSON 请求已直接写出，返回 null 表示处理完毕
     */
    private Object build(int code, String msg,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        response.setStatus(code);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
            return null;
        }

        ModelAndView mv = new ModelAndView("error/error");
        mv.addObject("code", code);
        mv.addObject("msg", msg);
        return mv;
    }
}
