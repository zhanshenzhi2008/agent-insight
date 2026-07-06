-- ============================================================================
-- ⚠️ ⚠️ ⚠️  本文件已废弃 (DEPRECATED) — 2026-07-03  ⚠️ ⚠️ ⚠️
-- ============================================================================
-- 原因：log_llm_* 4 张表的真实存储是 llm-agent 工程的 MongoDB，
--       不再属于本工程。详见 docs/00-revision-2026-07-03.md §1
--       与 docs/01-SRS.md v0.3。
--
-- 本工程不再创建/修改 log_llm_*。本文件暂时保留**仅供**：
--   (1) 老 5 个 Service 跑 mvn test 时的兜底造数
--   (2) 历史归档
--
-- 真正删除时间：W4 老 Service 全部切到 MongoQueryEngine 后
-- （详见 docs/00-revision-2026-07-03.md §3 实施计划 W4）
--
-- 替代方案（未来）：
--   - MongoDB fixture：fixtures/mongodb/init-llm-agent-log.js
--   - 模板：templates/llm-agent/tables/*.json
-- ============================================================================
-- Agent Insight — MySQL 测试数据造数脚本
-- 来源：原 docs/04-测试文档.md §6.1（已搬迁至此）
-- 用途：CI / 本地 mvn test 时执行，自动注入 log_llm_* 业务表测试数据
-- 用法：
--   mysql -h ${MYSQL_HOST:-localhost} -P ${MYSQL_PORT:-3306} \
--         -u ${MYSQL_USERNAME:-llm_agent} -p${MYSQL_PASSWORD:-llm_agent} \
--         ${MYSQL_DB:-llm_agent} < init-log-llm.sql
-- ----------------------------------------------------------------------------
-- 约束：
--   - 所有时间字段固定为常量，避免 NOW() 导致 CI 漂移
--   - 主键固定，幂等可重跑（使用 REPLACE INTO / ON DUPLICATE KEY UPDATE）
--   - 不含真实用户信息，仅供测试
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. log_llm_agent_main — Agent 实例主表
--   列必须与 agent-insight-server/.../LogLlmAgentMain.java 完全一致
--   （由 EntitySchemaContractTest 强制守护）。
-- ----------------------------------------------------------------------------
REPLACE INTO log_llm_agent_main
  (id, biz_id, request_id, agent_id, top_agent_name, is_entrance_agent,
   title, task_status, agent_status, success, create_by, create_time,
   agent_end_time)
VALUES
  (1, 1001, 'req_test_001', 1, 'DataAnalysisAgent', TRUE,
   'demo', 2, 1, TRUE, 1, '2026-06-30 10:00:00',
   '2026-06-30 10:00:05'),
  (2, 1001, 'req_test_001', 2, 'SubSearchAgent',    FALSE,
   'demo', 2, 1, TRUE, 1, '2026-06-30 10:00:02',
   '2026-06-30 10:00:04'),
  (3, 1002, 'req_test_002', 3, 'DataAnalysisAgent', TRUE,
   'demo', 3, 2, FALSE, 1, '2026-06-30 11:00:00',
   '2026-06-30 11:00:08'),
  (4, 1003, 'req_test_large', 4, 'BulkProcessAgent', TRUE,
   'demo', 2, 1, TRUE, 1, '2026-06-30 12:00:00',
   '2026-06-30 12:30:00');

-- ----------------------------------------------------------------------------
-- 2. log_llm_task_detail — 任务明细表
-- ----------------------------------------------------------------------------
REPLACE INTO log_llm_task_detail
  (id, log_llm_agent_main_id, request_id, agent_name, task_name,
   task_unique_name, task_type, task_index, full_path, success,
   result, error_message, create_time, task_end_time)
VALUES
  (1, 1, 'req_test_001', 'DataAnalysisAgent', 'analyze', 'task_analyze',
   'expression', 0, '/root/analyze', TRUE, '{"status":"ok"}',
   NULL, '2026-06-30 10:00:01', '2026-06-30 10:00:02'),
  (2, 1, 'req_test_001', 'DataAnalysisAgent', 'process', 'task_process',
   'expression', 1, '/root/process', FALSE, NULL,
   'NullPointerException at line 42', '2026-06-30 10:00:03', '2026-06-30 10:00:04'),
  (3, 2, 'req_test_001', 'SubSearchAgent', 'search', 'task_search',
   'expression', 0, '/root/search', TRUE, '{"hits":3}',
   NULL, '2026-06-30 10:00:02', '2026-06-30 10:00:03'),
  (4, 3, 'req_test_002', 'DataAnalysisAgent', 'analyze', 'task_analyze',
   'expression', 0, '/root/analyze', FALSE, NULL,
   'TimeoutException after 5000ms', '2026-06-30 11:00:00', '2026-06-30 11:00:05');

-- ----------------------------------------------------------------------------
-- 3. log_llm_task_step — 任务步骤表（template/rag/parser/verifier/action/result）
-- ----------------------------------------------------------------------------
REPLACE INTO log_llm_task_step
  (id, log_llm_task_detail_id, step_type, step_order,
   step_input, step_output, duration_ms, create_time)
VALUES
  (1, 1, 'template', 1, '{"prompt":"analyze:..."}', '{"rendered":"..."}', 50, '2026-06-30 10:00:01'),
  (2, 1, 'action',   2, '{}',                     '{"status":"ok"}',    800, '2026-06-30 10:00:02'),
  (3, 2, 'template', 1, '{"prompt":"process:..."}', '{}',                 50, '2026-06-30 10:00:03'),
  (4, 2, 'action',   2, '{}',                     NULL,                  50, '2026-06-30 10:00:04');

-- ----------------------------------------------------------------------------
-- 4. log_llm_http_request — LLM HTTP 请求日志
-- ----------------------------------------------------------------------------
REPLACE INTO log_llm_http_request
  (id, request_id, agent_name, task_unique_name,
   model, prompt_tokens, completion_tokens, total_tokens,
   http_status, duration_ms, error_message, create_time)
VALUES
  (1, 'req_test_001', 'DataAnalysisAgent', 'task_analyze',
   'gpt-4o', 120, 80, 200, 200, 850, NULL, '2026-06-30 10:00:01'),
  (2, 'req_test_001', 'SubSearchAgent',    'task_search',
   'gpt-4o', 60,  40, 100, 200, 450, NULL, '2026-06-30 10:00:02'),
  (3, 'req_test_002', 'DataAnalysisAgent', 'task_analyze',
   'gpt-4o', 150, 0,   150, 500, 5000, 'TimeoutException', '2026-06-30 11:00:00');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 验证（可选）：造数后跑一下，确认数据到位
-- SELECT COUNT(*) FROM log_llm_agent_main;       -- 期望 4
-- SELECT COUNT(*) FROM log_llm_task_detail;       -- 期望 4
-- SELECT COUNT(*) FROM log_llm_task_step;         -- 期望 4
-- SELECT COUNT(*) FROM log_llm_http_request;      -- 期望 3
-- ============================================================================