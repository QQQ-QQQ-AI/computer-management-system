package com.cafe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动配置自检。
 *
 * 在 Web 服务器启动之前打印关键配置的「最终生效值」。
 *
 * 为什么需要它：Spring Boot 的配置有十来个来源且优先级各不相同
 * （命令行参数 &gt; 系统属性 &gt; 环境变量 &gt; application.yml），
 * 一旦启动参数和配置文件不一致，只看配置文件很容易误判。
 * 例如环境变量 SERVER__PORT 会被宽松绑定识别为 server.port，从而覆盖 yml 中的设置。
 * 启动时把生效值打出来，这类问题一眼可见。
 */
@Slf4j
@Component
public class StartupConfigReporter {

    public StartupConfigReporter(Environment env) {
        log.info("""
                        
                        ---------- 启动配置 ----------
                          server.port                   = {}
                          spring.datasource.url         = {}
                          spring.datasource.username    = {}
                          mybatis-plus.mapper-locations = {}
                          spring.thymeleaf.cache        = {}
                        ------------------------------""",
                env.getProperty("server.port"),
                env.getProperty("spring.datasource.url"),
                env.getProperty("spring.datasource.username"),
                env.getProperty("mybatis-plus.mapper-locations"),
                env.getProperty("spring.thymeleaf.cache"));
    }
}
