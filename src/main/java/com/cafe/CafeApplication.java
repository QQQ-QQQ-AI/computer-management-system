package com.cafe;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 基于 Java 的网咖管理系统 —— 启动类
 *
 * @author 胡政宇
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.cafe.mapper")
public class CafeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeApplication.class, args);
    }

    /**
     * 服务器真正就绪后打印访问地址。
     *
     * 用 WebServerInitializedEvent 而不是在 main 里写死端口：
     * 端口无论来自配置文件、命令行参数还是环境变量，这里拿到的都是最终生效值，
     * 避免出现「提示写 8080、实际跑在别的端口」这种误导。
     */
    @Bean
    public ApplicationListener<WebServerInitializedEvent> startedBanner(Environment env) {
        return event -> {
            int port = event.getWebServer().getPort();
            log.info("配置解析结果 -> server.port={} | spring.datasource.url={}",
                    env.getProperty("server.port"),
                    env.getProperty("spring.datasource.url"));
            log.info("""

                    ============================================
                      网咖管理系统启动成功，实际端口：{}
                      登录入口   : http://localhost:{}/login
                      会员自助端 : http://localhost:{}/member
                      收银员端   : http://localhost:{}/cashier
                      管理员端   : http://localhost:{}/admin
                    ============================================
                    """, port, port, port, port, port);
        };
    }
}
