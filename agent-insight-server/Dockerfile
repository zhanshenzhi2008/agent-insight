# =============================================================================
# Agent Insight 后端 Dockerfile
# 基于 eclipse-temurin:21 构建，暴露 9280 端口
# 构建: docker build -f Dockerfile -t agent-insight-server:1.0.0 .
# 运行: docker run -d -p 9280:9280 --name agent-insight-server agent-insight-server:1.0.0
# =============================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl tini

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 9280

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9280/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

CMD ["/docker-entrypoint.sh"]
