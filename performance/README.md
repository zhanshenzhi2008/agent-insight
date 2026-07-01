# Agent Insight 性能测试脚本

本目录包含 JMeter XML 格式（`.jmx`）的性能测试脚本，对应 `docs/04-测试文档.md` 中的 PT-01 ~ PT-08 用例。

## 目录结构

```
performance/
├── PT-01.jmx   # 单 requestId 查询（仅 DB）
├── PT-02.jmx   # 单 requestId 查询（DB + 文件）
├── PT-03.jmx   # 日志正则搜索
├── PT-04.jmx   # 100 并发请求检索
├── PT-05.jmx   # Data Explorer 分页查询
├── PT-06.jmx   # 10 用户同时分析不同请求
├── PT-07.jmx   # 10 用户同时查询不同数据源
├── PT-08.jmx   # Redis 缓存命中率
└── README.md
```

## 环境要求

- JMeter 5.6+
- JDK 21
- 后端服务运行于 `localhost:9280`
- 测试数据准备：
  - MySQL `llm_agent` 库中有 `log_llm_agent_main`、`log_llm_task_detail`、`log_llm_http_request` 表且有测试数据
  - 日志文件路径 `/app/project/data/user/{username}/{yyyyMM}/{requestId}.log` 可访问
  - MongoDB `agent_insight` 库有 Data Explorer 元数据（数据源 + 表配置）
  - Redis 服务可用

## 如何运行

### 方法一：命令行运行

```bash
# 进入项目根目录
cd /path/to/agent-insight

# 单个脚本
jmeter -n -t performance/PT-01.jmx -l performance/results/PT-01-result.jtl -e -o performance/results/PT-01-report

# 批量运行（Linux/macOS）
for i in performance/PT-*.jmx; do
  name=$(basename "$i" .jmx)
  mkdir -p "performance/results/$name"
  jmeter -n -t "$i" \
    -l "performance/results/$name-result.jtl" \
    -e -o "performance/results/$name-report"
done
```

### 方法二：GUI 打开

1. 启动 JMeter GUI：`jmeter`
2. File → Open → 选择 `.jmx` 文件
3. 点击绿色运行按钮或 Ctrl+R

### 方法三：Docker 运行

```bash
docker run --rm \
  -v $(pwd)/performance:/workspace/performance \
  -v $(pwd)/performance/results:/workspace/results \
  justb4/jmeter:latest \
  jmeter -n \
    -t /workspace/performance/PT-01.jmx \
    -l /workspace/results/PT-01-result.jtl \
    -e -o /workspace/results/PT-01-report
```

## 测试用例说明

### PT-01 — 单 requestId 查询（仅 DB）
- **目标 P99**：< 500ms
- **场景**：精确查询单个 requestId 的概览数据（仅查 MySQL）
- **HTTP 请求**：`GET /api/v1/requests/{requestId}/overview`
- **断言**：响应时间 < 500ms，响应 code=0

### PT-02 — 单 requestId 查询（DB + 文件）
- **目标 P99**：< 2s
- **场景**：查询 requestId 的概览 + 读取日志文件（DB + 文件系统）
- **HTTP 请求**：`GET /api/v1/requests/{requestId}/overview` + `GET /api/v1/requests/{requestId}/log`
- **断言**：响应时间 < 2s

### PT-03 — 日志正则搜索
- **目标 P99**：< 3s
- **场景**：在日志文件中使用正则表达式搜索关键词
- **HTTP 请求**：`GET /api/v1/requests/{requestId}/log/search?keyword=\\d{3}&regex=true`
- **断言**：响应时间 < 3s，data 数组长度 >= 0

### PT-04 — 100 并发请求检索
- **目标吞吐量**：> 50 req/s
- **场景**：100 个并发线程同时发送请求检索列表
- **HTTP 请求**：`GET /api/v1/requests?page=0&size=20`
- **断言**：汇总报告吞吐量 > 50 req/s，P99 < 5s

### PT-05 — Data Explorer 分页查询
- **目标 P99**：< 1s（MySQL/PG），< 2s（MongoDB）
- **场景**：执行分页查询（带过滤和排序）
- **HTTP 请求**：`POST /api/v1/explorer/query/execute`
- **断言**：响应时间 < 阈值，响应 data 数组非空

### PT-06 — 10 用户同时分析不同请求
- **目标 P99**：< 2s
- **场景**：10 个并发用户同时分析不同 requestId 的执行轨迹
- **HTTP 请求**：`GET /api/v1/requests/{requestId}/trace`
- **断言**：响应时间 < 2s

### PT-07 — 10 用户同时查询不同数据源
- **目标 P99**：< 3s
- **场景**：10 个并发用户同时查询不同数据源
- **HTTP 请求**：`POST /api/v1/explorer/query/execute`（不同 datasourceKey）
- **断言**：响应时间 < 3s

### PT-08 — Redis 缓存命中率
- **目标缓存率**：> 80%（相同查询重复请求）
- **场景**：同一查询执行两次，第二次应命中 Redis 缓存
- **HTTP 请求**：`POST /api/v1/explorer/query/execute`（相同参数执行两次）
- **断言**：第二次响应时间明显短于第一次（差值 > 50%）
- **辅助验证**：`redis-cli INFO stats | grep keyspace_hits`

## 测试报告

JMeter HTML 报告生成在 `performance/results/{PT-xx}-report/index.html`，包含：
- Apdex 评分
- P50/P90/P95/P99 响应时间
- 吞吐量（req/s）
- 错误率
- 时间序列图

## 常见问题

**Q: 报 `NonHTTPResponseCode: java.net.ConnectException`**
A: 后端服务未启动，请先确认 `http://localhost:9280/actuator/health` 返回 UP。

**Q: 报 401/403**
A: 检查是否有鉴权中间件，前端测试脚本可能需要先获取 Token。

**Q: 日志文件不存在**
A: 确认 `application.yml` 中 `agent-insight.log.data-root` 配置的路径存在，且有对应测试数据。
