# =============================================================================
# Agent Insight 后端 Dockerfile
# 基于 eclipse-temurin:21 构建，暴露 9280 端口
# 构建: docker build -f agent-insight-server/Dockerfile -t agent-insight-server:1.0.0 .
# 运行: docker run -d -p 9280:9280 --name agent-insight-server agent-insight-server:1.0.0
# =============================================================================
#
# 多模块 Maven 项目说明：
#   - context 使用仓库根目录 "."
#   - 通过 -pl agent-insight-server -am 只构建后端模块
#   - 需要复制根目录 pom.xml 以解析 parent POM
#
# 镜像分层说明：
#   - builder 阶段：用于编译，会包含源码，但只在构建时存在
#   - final 阶段：只复制 jar，不包含源码，用于运行
#
# =============================================================================

FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 先只复制 POM，利用 Docker layer cache 缓存 Maven 依赖
# 只要 pom.xml 不变，这一层就会命中缓存，不会重复下载依赖
COPY pom.xml ./pom.xml
COPY agent-insight-server/pom.xml ./agent-insight-server/pom.xml

# 下载依赖（不编译）
RUN mvn dependency:go-offline -pl agent-insight-server -am -q

# 再复制源码，编译打包
COPY agent-insight-server/src ./agent-insight-server/src
RUN mvn package -DskipTests -q -pl agent-insight-server -am

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl tini

COPY --from=builder /app/agent-insight-server/target/*.jar app.jar

EXPOSE 9280

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9280/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

CMD ["/docker-entrypoint.sh"]
