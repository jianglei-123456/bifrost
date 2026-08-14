package com.bifrost.bootstrap.config;

import com.bifrost.common.util.FileIO;
import com.bifrost.core.config.BifrostProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * SQLite 数据源配置。
 *
 * <p>由 {@code bifrost.db.path} 生成连接串（WAL / busy_timeout / 外键启用），
 * 并在建连前确保数据目录与封面缓存目录存在（《整体技术架构》§5、§6）。</p>
 */
@Configuration
public class SqliteDataSourceConfig {

    @Bean
    public DataSource dataSource(BifrostProperties properties) {
        Path dbPath = properties.getDb().getPath().toAbsolutePath().normalize();
        FileIO.ensureDirs(dbPath.getParent());
        FileIO.ensureDirs(properties.getMedia().getCoverCacheDir().toAbsolutePath().normalize());

        String url = "jdbc:sqlite:" + dbPath + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on";
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(url);
        return dataSource;
    }
}
