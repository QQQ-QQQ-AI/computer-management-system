package com.cafe.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** Protect every unsafe HTTP method, including login and registration. */
@Configuration
public class CsrfConfig {
    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        var registration = new FilterRegistrationBean<>(new CsrfFilter(new HttpSessionCsrfTokenRepository()));
        registration.setOrder(-100);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
