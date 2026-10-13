# =============================================================================
# Agent Insight 前端 Dockerfile（对齐 ai-job-hunter-fe：Node 22 + nginx 静态托管）
# 构建: docker build -f Dockerfile -t agent-insight/frontend:latest .
# =============================================================================

FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --legacy-peer-deps

COPY . .

# 静态托管没有运行时 Vite 配置；API 基址必须在构建时写入产物。
# /api = 浏览器请求当前域名 /api/*，由本容器 Nginx 转发到后端（方案 A）。
# 不要写成 http://localhost:9280（会打进公网用户浏览器）。
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV NODE_ENV=production

RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 健康检查也可写在 compose（与 ai-job-hunter-fe 一致）；此处保留一份兜底
# BusyBox wget：仅用 -q --spider；失败 → unhealthy → Traefik v3 过滤路由
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:80/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
