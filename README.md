# Bifrost

家庭媒体库管理平台：统一管理 **视频 / 音频 / 电子书** 三类媒体资源，并与 Jellyfin、Sonic 等外部服务对接、发布 OPDS 图书流。

## 技术栈

- Java 21
- Spring Boot 3.5.x
- Maven 多模块

## 模块结构

```
bifrost (父级 POM，统一 Spring Boot 与依赖版本)
├── bifrost-common        # 工具包（字符串、日期、文件 IO、自定义异常）
├── bifrost-domain        # 核心实体（Entity）、DTO、枚举，以及 Repository 接口
├── bifrost-core          # 核心业务逻辑（Service 层）
│   ├── video/            # 包隔离：视频扫描、索引逻辑
│   ├── audio/            # 包隔离：音频标签解析
│   └── book/             # 包隔离：电子书元数据解析
├── bifrost-adapter       # 【关键层】对接外部客户端的具体实现（聚合 POM）
│   ├── jellyfin-client   # 向 Jellyfin 推送 / 同步的适配器
│   ├── sonic-client      # 向 Sonic 推送的适配器
│   └── opds-publisher    # 生成图书 OPDS 流的适配器
├── bifrost-api           # RESTful Controller 层（供 Vue Dashboard 调用）
└── bifrost-bootstrap     # Spring Boot 启动类，存放 application.yml 配置文件
```

## 快速开始

```bash
# 使用 Maven Wrapper 构建（首次会自动下载指定 Maven 版本）
./mvnw clean verify

# 启动应用
./mvnw -pl bifrost-bootstrap -am spring-boot:run
```

应用默认监听 `http://localhost:8080`。
