# Bifrost 单镜像：后端 fat jar（内含管理端产物）+ JRE（见 ADR-0007）
#
# 这个镜像是"纯打包"：不在镜像里跑 Maven / Node（决策见操作手册 05 §为什么不在镜像里构建）。
# 构建前提（两步都在宿主完成）：
#   1) 管理端产物：../bifrost-dashboard 构建 --base=/admin/ 后拷到
#      bifrost-bootstrap/src/main/resources/static/admin/（该目录已 gitignore）
#   2) 后端 jar：./mvnw clean verify  → bifrost-bootstrap/target/bifrost-bootstrap-*.jar
#
# 构建（在仓库根目录执行，上下文就是仓库根）：
#   docker build -f docker/Dockerfile \
#     --build-arg APP_VERSION=1.0.0 --build-arg GIT_REVISION=$(git rev-parse --short HEAD) \
#     -t ghcr.io/jianglei-123456/bifrost:1.0.0 .

FROM eclipse-temurin:21-jre

# curl：HEALTHCHECK 与容器内排障（《整体技术架构》§9.1 已按 curl 写）
# tzdata：TZ 生效需要它（定时扫描 cron 用 JVM 默认时区）
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行：固定 uid/gid 1000（宿主 /data 目录需 chown 1000:1000，见操作手册 05）。
#
# 这里必须写成幂等：eclipse-temurin 的 Ubuntu 基底**自带 ubuntu:ubuntu = 1000**，
# 直接 `groupadd -g 1000` 会在构建时报 "GID '1000' already exists"（实测）。
# 基底已有就用基底的，没有才建；`USER` 也一律用数字形式，不依赖 /etc/passwd 里的名字。
RUN set -eux; \
    if ! getent group 1000 >/dev/null; then groupadd -g 1000 bifrost; fi; \
    if ! getent passwd 1000 >/dev/null; then useradd -u 1000 -g 1000 -m -s /usr/sbin/nologin bifrost; fi; \
    mkdir -p /app /data; \
    chown -R 1000:1000 /app /data

WORKDIR /app

# 唯一的产物：自带管理端的 fat jar（版本号变了也不用改这里，通配即可；
# .original 后缀的瘦 jar 不匹配这个模式）
COPY bifrost-bootstrap/target/bifrost-bootstrap-*.jar /app/bifrost.jar

# BIFROST_DATA_DIR=/data：db / 封面缓存 / 日志 / secret.key 全在卷里（备份=备份该卷）
ENV BIFROST_DATA_DIR=/data \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

VOLUME ["/data"]
EXPOSE 18080

# 版本可追溯：1.0.0 起 master 常驻发布号，本地构建与发布产物同名，靠 revision 区分
ARG APP_VERSION=1.0.0
ARG GIT_REVISION=unknown
LABEL org.opencontainers.image.title="Bifrost" \
      org.opencontainers.image.description="家庭媒体库管理平台：音乐 / 图书管理 + Subsonic / OPDS / KOSync 协议服务 + 管理端" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_REVISION}" \
      org.opencontainers.image.source="https://github.com/jianglei-123456/bifrost"

# 数字形式：基底自带 uid 1000（ubuntu）时不会创建 bifrost 用户，写名字会报 "unable to find user"
USER 1000:1000

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:18080/api/ping || exit 1

# exec 形式包一层 sh：让 JAVA_OPTS 可覆盖，同时保证 SIGTERM 能传到 java（干净停机）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/bifrost.jar"]
