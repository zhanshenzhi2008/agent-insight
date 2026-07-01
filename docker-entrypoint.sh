#!/bin/sh
# =============================================================================
# Agent Insight 后端启动脚本（docker-entrypoint.sh）
# 支持从环境变量注入配置，支持 profile 切换
# =============================================================================

set -e

# 默认 profile
PROFILE="${SPRING_PROFILES_ACTIVE:-default}"

echo "[Agent Insight] Starting with profile: $PROFILE"
echo "[Agent Insight] JAVA_OPTS: $JAVA_OPTS"

# 如果外部配置文件挂载到 /config/application.yml，复制到运行目录
if [ -f "/config/application.yml" ]; then
    echo "[Agent Insight] Using external config from /config/application.yml"
    cp /config/application.yml /app/config.yml
fi

# 执行 Spring Boot JAR
exec java $JAVA_OPTS -jar app.jar \
    --spring.profiles.active="$PROFILE" \
    "$@"
