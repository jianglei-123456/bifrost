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

    /** 数据目录（db / 封面缓存 / 日志的默认根） */
    private Data data = new Data();

    /** SQLite 数据库 */
    private Db db = new Db();

    /** 媒体库根（预留未实现——库根的唯一写入通道是管理端 REST，见下方说明） */
    private Library library = new Library();

    /** 媒体通用配置 */
    private Media media = new Media();

    /** 扫描配置 */
    private Scan scan = new Scan();

    /** 认证配置 */
    private Auth auth = new Auth();

    /** Subsonic 服务配置 */
    private Subsonic subsonic = new Subsonic();

    /** OPDS 配置（M2-book） */
    private Opds opds = new Opds();

    /** 阅读进度同步（KOSync）配置（M3-sync） */
    private Kosync kosync = new Kosync();

    /** 跨域（CORS）配置 */
    private Cors cors = new Cors();

    /** 数据目录配置 */
    @Getter
    @Setter
    public static class Data {
        /** 数据目录根：db、封面缓存、日志默认都挂在它下面（容器里指向 /data） */
        private Path dir = Path.of("./data");
    }

    /** SQLite 数据库配置 */
    @Getter
    @Setter
    public static class Db {
        /** SQLite 文件路径（默认 ${bifrost.data.dir}/bifrost.db，可单独覆盖） */
        private Path path = Path.of("./data/bifrost.db");
    }

    /**
     * 媒体库根配置（<b>预留未实现</b>）。
     *
     * <p>库根（音乐目录 / 图书目录）的唯一写入通道是管理端 REST
     * （{@code /api/music-roots}、{@code /api/book-roots}），落在 DB 的 {@code library_root} 表；
     * 本类没有任何消费者——在 yml 里写 {@code bifrost.library.roots} 不会生效。
     * 容器部署时请挂载媒体目录后用管理端添加库根（操作手册 05）。</p>
     */
    @Getter
    @Setter
    public static class Library {
        /** 库根列表：name / path / enabled / mediaType（预留，暂无消费者） */
        private List<Root> roots = new ArrayList<>();

        @Getter
        @Setter
        public static class Root {
            /** 库根名称（Subsonic musicFolder 名 / OPDS 显示名） */
            private String name;
            /** 库根目录绝对路径 */
            private String path;
            /** 是否启用 */
            private Boolean enabled = true;
            /** 媒体类型（v2 增；MUSIC/BOOK；默认 MUSIC 兼容历史 yml） */
            private com.bifrost.domain.enums.MediaType mediaType = com.bifrost.domain.enums.MediaType.MUSIC;
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
        /** 定时扫描 cron（6 段含秒，Spring CronExpression 要求；空=禁用） */
        private String cron = "0 0 3 * * *";
        /** 事务批大小（文件数） */
        private int batchSize = 200;
    }

    /** OPDS 配置（M2-book） */
    @Getter
    @Setter
    public static class Opds {
        /** 是否要求 HTTP Basic 认证（默认 false=匿名，Q6-C） */
        private boolean requireAuth = false;
    }

    /** 阅读进度同步（KOSync）配置（M3-sync） */
    @Getter
    @Setter
    public static class Kosync {
        /** 协议开关（false = 不注册端点与安全链；关闭后 5 个端点必须 404） */
        private boolean enabled = true;
        /** 是否放行 POST /users/create 的自助注册（R2b） */
        private boolean registrationEnabled = true;
        /** 未匹配的新文档指纹是否触发一次图书扫描（R5/C1：无冷却、同一指纹只触发一次） */
        private boolean autoScanOnUnmatched = true;
        /** 展示给用户抄进阅读器的同步服务地址；留空则由管理端按"当前主机名 + server.port"拼 */
        private String publicBaseUrl = "";
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

    /** 跨域配置 */
    @Getter
    @Setter
    public static class Cors {
        /** 允许的跨域来源（Origin）；空列表=禁用 CORS。默认放行所有来源，生产建议按需收紧。 */
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
    }
}
