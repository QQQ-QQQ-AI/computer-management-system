package com.cafe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册拦截器。
 * 只拦截三个角色端路径，登录页、注册页与静态资源放行。
 */
@Configuration
@lombok.RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/member/**", "/cashier/**", "/admin/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**");
    }
}
