package com.bifrost.bootstrap;

import com.bifrost.common.util.FileIO;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Path;

/**
 * Bifrost 应用启动入口。
 *
 * <p>{@code scanBasePackages = "com.bifrost"} 跨模块扫描（bifrost-api 的 Controller、
 * bifrost-core 的 Service 等）；{@code @EntityScan} / {@code @EnableJpaRepositories} 扫描
 * bifrost-domain 的实体与仓储（多模块装配，见《整体技术架构》§4）；启动前创建默认数据目录。</p>
 */
@SpringBootApplication(scanBasePackages = "com.bifrost")
@EntityScan(basePackages = "com.bifrost")
@EnableJpaRepositories(basePackages = "com.bifrost")
@ConfigurationPropertiesScan(basePackages = "com.bifrost")
public class BifrostApplication {

    public static void main(String[] args) {
        ensureDefaultDataDirs();
        SpringApplication.run(BifrostApplication.class, args);
    }

    /**
     * 确保默认数据目录存在（数据库父目录 + 封面缓存目录）。
     * 显式配置了 {@code bifrost.db.path} / {@code bifrost.media.cover-cache-dir} 时由
     * {@link com.bifrost.bootstrap.config.SqliteDataSourceConfig} 负责，此处仅兜底默认值。
     */
    private static void ensureDefaultDataDirs() {
        String dbPath = System.getProperty("bifrost.db.path",
                System.getenv().getOrDefault("BIFROST_DB_PATH", "./data/bifrost.db"));
        Path parent = Path.of(dbPath).toAbsolutePath().normalize().getParent();
        if (parent != null) {
            FileIO.ensureDirs(parent);
        }
        FileIO.ensureDirs(Path.of("./data/covers").toAbsolutePath().normalize());
    }
}
