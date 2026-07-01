# =============================================================================
# Agent Insight 后端 Dockerfile
# 基于 openjdk:21-jdk-slim 构建，暴露 9280 端口
# 构建: docker build -f Dockerfile -t agent-insight-server:1.0.0 .
# 运行: docker run -d -p 9280:9280 --name agent-insight-server agent-insight-server:1.0.0
# =============================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# 只复制 pom.xml 和源码，避免每次修改代码都重新下载依赖
COPY pom.xml .
COPY src ./src

# 构建 JAR（跳过测试）
RUN mvn package -DskipTests -q

# 运行时镜像
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装 curl（健康检查用）和 tini（信号处理）
RUN apk add --no-cache curl tini

# 复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口
EXPOSE 9280

# JVM 调优参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9280/actuator/health || exit 1

# 信号转发
ENTRYPOINT ["/sbin/tini", "--"]

# 启动脚本（支持环境变量覆盖配置）
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

CMD ["/docker-entrypoint.sh"]
