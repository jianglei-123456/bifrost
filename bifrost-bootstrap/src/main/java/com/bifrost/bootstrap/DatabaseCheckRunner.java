package com.bifrost.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 启动时 SQLite 可读写校验（《通用功能说明》§11 健康检查）。
 *
 * <p>校验失败 → 抛异常终止启动，并给出明确日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseCheckRunner implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SELECT 1");
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 数据库不可读写，启动失败", e);
        }
        log.info("SQLite 数据库可读写校验通过");
    }
}
