package com.bifrost.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bifrost 应用启动入口。
 *
 * <p>使用 {@code scanBasePackages = "com.bifrost"} 跨模块扫描
 * （bifrost-api 的 Controller、bifrost-core 的 Service 等）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.bifrost")
public class BifrostApplication {

    public static void main(String[] args) {
        SpringApplication.run(BifrostApplication.class, args);
    }
}
