#!/bin/sh
# =============================================================================
# Agent Insight 后端启动脚本（docker-entrypoint.sh）
# 启动 Spring Boot JAR，支持从环境变量注入配置与 JVM 参数
# =============================================================================

set -e

echo "[Agent Insight] JAVA_OPTS: $JAVA_OPTS"

# 如果外部配置文件挂载到 /config/application.yml，复制到运行目录
if [ -f "/config/application.yml" ]; then
    echo "[Agent Insight] Using external config from /config/application.yml"
    cp /config/application.yml /app/config.yml
fi

# 执行 Spring Boot JAR
exec java $JAVA_OPTS -jar app.jar "$@"