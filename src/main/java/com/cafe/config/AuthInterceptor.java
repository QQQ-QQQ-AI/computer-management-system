package com.cafe.config;

import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录与角色拦截器。
 *
 * 三道防线中的第二道（第一道是登录校验，第三道是 Service 层的数据归属校验）：
 * 1. 未登录 → 跳转登录页；
 * 2. 已登录但访问了不属于自己角色的路径前缀 → 跳回自己的首页。
 *
 * 路径前缀与角色的对应关系：/member → MEMBER，/cashier → CASHIER，/admin → ADMIN
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final com.cafe.mapper.MemberMapper memberMapper;
    private final com.cafe.mapper.SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String ctx = request.getContextPath();

        HttpSession session = request.getSession(false);
        LoginUser user = (session == null)
                ? null
                : (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);

        // 1. 未登录
        if (user == null) {
            response.sendRedirect(ctx + "/login");
            return false;
        }

        boolean valid;
        if (Constants.ROLE_MEMBER.equals(user.getRole())) {
            var current = memberMapper.selectById(user.getId());
            valid = current != null && Integer.valueOf(Constants.ENABLED).equals(current.getStatus())
                    && java.util.Objects.equals(user.getCredentialStamp(), com.cafe.common.PasswordUtil.stamp(current.getPassword()));
        } else {
            var current = sysUserMapper.selectById(user.getId());
            valid = current != null && Integer.valueOf(Constants.ENABLED).equals(current.getStatus())
                    && java.util.Objects.equals(user.getRole(), current.getRole())
                    && java.util.Objects.equals(user.getCredentialStamp(), com.cafe.common.PasswordUtil.stamp(current.getPassword()));
        }
        if (!valid) { session.invalidate(); response.sendRedirect(ctx + "/login"); return false; }
        // 使用 MVC 已匹配的路由，避免编码路径与原始 URI 的角色判断不一致。
        Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String uri = pattern == null ? "" : pattern.toString();
        // 2. 角色越权访问
        String required = requiredRole(uri);
        if (required == null || !required.equals(user.getRole())) {
            response.sendRedirect(ctx + homeOf(user.getRole()));
            return false;
        }

        return true;
    }

    /** 根据请求路径判断所需角色，返回 null 表示不限制 */
    private String requiredRole(String uri) {
        if (uri.startsWith("/admin")) {
            return Constants.ROLE_ADMIN;
        }
        if (uri.startsWith("/cashier")) {
            return Constants.ROLE_CASHIER;
        }
        if (uri.startsWith("/member")) {
            return Constants.ROLE_MEMBER;
        }
        return null;
    }

    /** 各角色登录后的默认首页 */
    private String homeOf(String role) {
        return switch (role) {
            case Constants.ROLE_ADMIN -> "/admin";
            case Constants.ROLE_CASHIER -> "/cashier";
            default -> "/member";
        };
    }
}
