# =============================================================================
# Agent Insight 后端 Dockerfile（对齐 ai-job-hunter-be 小内存 JVM 约定）
# 构建: docker build -f agent-insight-server/Dockerfile -t agent-insight/backend:latest .
# =============================================================================
#
# 多模块 Maven：context 为仓库根；-pl agent-insight-server -am
# =============================================================================

FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml ./pom.xml
COPY agent-insight-server/pom.xml ./agent-insight-server/pom.xml

RUN mvn dependency:go-offline -pl agent-insight-server -am -q

COPY agent-insight-server/src ./agent-insight-server/src
RUN mvn package -DskipTests -q -pl agent-insight-server -am

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl tini

COPY --from=builder /app/agent-insight-server/target/*.jar app.jar

EXPOSE 9280

# 小机器防 OOM：按容器内存上限吃堆（compose mem_limit 建议 512m~768m）
# 可用环境变量覆盖：JAVA_OPTS
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=15.0 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

# Spring Boot Actuator（需 spring-boot-starter-actuator；Security 须放行）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://127.0.0.1:9280/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]

COPY agent-insight-server/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

CMD ["/docker-entrypoint.sh"]
