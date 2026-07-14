# =============================================================================
# Agent Insight 前端 Dockerfile
# 基于 nginx:alpine 构建，暴露 80 端口
# 构建: docker build -f Dockerfile -t agent-insight-web:1.0.0 .
# 运行: docker run -d -p 80:80 --name agent-insight-web agent-insight-web:1.0.0
# =============================================================================

FROM node:20-alpine AS builder

WORKDIR /app

# 先只复制依赖描述文件，利用 Docker layer cache 缓存 npm 依赖
# 只要 package.json 不变，这一层就会命中缓存，不会重复安装依赖
COPY package.json package-lock.json* ./

RUN npm ci --legacy-peer-deps

# 再复制源码，构建静态资源
COPY . .

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
