package com.bifrost.bootstrap;

import com.bifrost.common.util.FileIO;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

/**
 * Bifrost 应用启动入口。
 *
 * <p>{@code scanBasePackages = "com.bifrost"} 跨模块扫描（bifrost-api 的 Controller、
 * bifrost-core 的 Service 等）；{@code @EntityScan} / {@code @EnableJpaRepositories} 扫描
 * bifrost-domain 的实体与仓储（多模块装配，见《整体技术架构》§4）；启动前创建默认数据目录。</p>
 *
 * <p>{@code @EnableScheduling} 供 {@code ScanScheduler}（{@code bifrost.scan.cron}）使用；
 * cron 留空时该组件不注册，调度器空转。</p>
 */
@SpringBootApplication(scanBasePackages = "com.bifrost")
@EntityScan(basePackages = "com.bifrost")
@EnableJpaRepositories(basePackages = "com.bifrost")
@ConfigurationPropertiesScan(basePackages = "com.bifrost")
@EnableScheduling
public class BifrostApplication {

    /** 数据目录根键（系统属性 / 环境变量同名写法） */
    private static final String DATA_DIR_PROPERTY = "bifrost.data.dir";
    private static final String DATA_DIR_ENV = "BIFROST_DATA_DIR";
    private static final String DEFAULT_DATA_DIR = "./data";

    public static void main(String[] args) {
        ensureDefaultDataDirs();
        SpringApplication.run(BifrostApplication.class, args);
    }

    /**
     * 确保数据目录存在（数据目录根 + 封面缓存 + 日志），并兜底创建显式指定的 db 父目录。
     *
     * <p>目录根取 {@code bifrost.data.dir}：系统属性 → 环境变量 {@code BIFROST_DATA_DIR} →
     * 默认 {@code ./data}。真正的属性绑定由 Spring 完成，这里只是抢在日志与数据库初始化之前
     * 把目录建好（否则 logback 找不到目录、SQLite 建库失败）。</p>
     */
    private static void ensureDefaultDataDirs() {
        String dataDir = System.getProperty(DATA_DIR_PROPERTY,
                System.getenv().getOrDefault(DATA_DIR_ENV, DEFAULT_DATA_DIR));
        Path base = Path.of(dataDir).toAbsolutePath().normalize();
        FileIO.ensureDirs(base);
        FileIO.ensureDirs(base.resolve("covers"));
        FileIO.ensureDirs(base.resolve("logs"));

        // 单独覆盖过 db 路径时，也把它的父目录建好（默认已在 base 下）
        String dbPath = System.getProperty("bifrost.db.path", System.getenv("BIFROST_DB_PATH"));
        if (dbPath != null && !dbPath.isBlank()) {
            Path parent = Path.of(dbPath).toAbsolutePath().normalize().getParent();
            if (parent != null) {
                FileIO.ensureDirs(parent);
            }
        }
    }
}
