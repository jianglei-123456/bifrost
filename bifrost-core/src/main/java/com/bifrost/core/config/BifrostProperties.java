package com.bifrost.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bifrost 配置属性（前缀 {@code bifrost.*}）。
 *
 * <p>配置键表见《音乐管理技术设计》§10 与《整体技术架构》§6；
 * 覆盖机制：环境变量 / 启动参数 > 外部 yml > 内置 application.yml。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bifrost")
public class BifrostProperties {

    /** SQLite 数据库 */
    private Db db = new Db();

    /** 媒体库根 */
    private Library library = new Library();

    /** 媒体通用配置 */
    private Media media = new Media();

    /** 扫描配置 */
    private Scan scan = new Scan();

    /** 认证配置 */
    private Auth auth = new Auth();

    /** Subsonic 服务配置 */
    private Subsonic subsonic = new Subsonic();

    /** SQLite 数据库配置 */
    @Getter
    @Setter
    public static class Db {
        /** SQLite 文件路径 */
        private Path path = Path.of("./data/bifrost.db");
    }

    /** 媒体库根配置 */
    @Getter
    @Setter
    public static class Library {
        /** 库根列表：name / path / enabled */
        private List<Root> roots = new ArrayList<>();

        @Getter
        @Setter
        public static class Root {
            /** 库根名称（Subsonic musicFolder 名） */
            private String name;
            /** 库根目录绝对路径 */
            private String path;
            /** 是否启用 */
            private Boolean enabled = true;
        }
    }

    /** 媒体通用配置 */
    @Getter
    @Setter
    public static class Media {
        /** 封面缓存目录 */
        private Path coverCacheDir = Path.of("./data/covers");
    }

    /** 扫描配置 */
    @Getter
    @Setter
    public static class Scan {
        /** 定时扫描 cron；空=禁用 */
        private String cron = "0 3 * * *";
        /** 事务批大小（文件数） */
        private int batchSize = 200;
    }

    /** 认证配置 */
    @Getter
    @Setter
    public static class Auth {
        /** 初始管理员用户名 */
        private String initialUsername = "admin";
    }

    /** Subsonic 服务配置 */
    @Getter
    @Setter
    public static class Subsonic {
        /** Subsonic 服务开关 */
        private boolean enabled = true;
        /** 对外宣称 API 版本 */
        private String apiVersion = "1.16.1";
    }
}
