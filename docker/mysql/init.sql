-- =============================================================================
-- ⚠️ ⚠️ ⚠️  本文件已废弃 (DEPRECATED) — 2026-07-03  ⚠️ ⚠️ ⚠️
-- =============================================================================
-- 本文件创建的是 MySQL 上的 log_llm_* 4 张业务表。
-- 真实数据源已变更：
--   - log_llm_agent_main / log_llm_task_detail / log_llm_task_step /
--     log_llm_http_request 实际存储在 **llm-agent 工程的 MongoDB** 内
--   - 本工程不再创建、修改、备份这 4 张表
--
-- 详见：
--   docs/00-revision-2026-07-03.md §1
--   docs/01-SRS.md v0.3
--
-- 暂时保留原因：
--   老 5 个 Service (LogViewer/TraceAnalysis/RequestSearch/LlmCall/Source)
--   还在用 JPA Repository 访问这里，W4 切到 MongoQueryEngine 后即可删除。
--
-- 替代方案：
--   - llm-agent 工程自行初始化自己的 Mongo collection
--   - 本工程通过 templates/llm-agent/tables/*.json 表达元数据
--   - 不再有任何本地 MySQL 业务表
-- =============================================================================
-- Agent Insight MySQL 初始化脚本
-- 首次启动 docker-compose 时自动执行
-- =============================================================================

-- 创建 llm_agent 数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `llm_agent`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `llm_agent`;

-- ---------------------------------------------------------------------------
-- log_llm_agent_main: Agent 实例表（入口 + 子 Agent）
-- 注意：列必须与 agent-insight-server/.../LogLlmAgentMain.java 完全一致
--       （由 EntitySchemaContractTest 强制守护）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `log_llm_agent_main` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `biz_id`        VARCHAR(64) COMMENT '业务 ID',
    `request_id`    VARCHAR(64) NOT NULL COMMENT '请求唯一 ID',
    `agent_id`      BIGINT COMMENT 'Agent 实例 ID',
    `top_agent_name` VARCHAR(100) COMMENT '顶层 Agent 名称',
    `is_entrance_agent` BIT(1) COMMENT '是否入口 Agent',
    `title`         VARCHAR(100) COMMENT '标题',
    `description`   VARCHAR(1000) COMMENT '描述',
    `task_status`   INT COMMENT '任务状态',
    `agent_status`  INT COMMENT 'Agent 状态',
    `shift_must_task` INT COMMENT '是否必须任务',
    `agent_try_count` INT COMMENT 'Agent 尝试次数',
    `task_try_count` INT COMMENT '任务尝试次数',
    `task_index`    INT COMMENT '执行顺序',
    `success`       BIT(1) COMMENT '是否成功',
    `agent_result`  MEDIUMTEXT COMMENT 'Agent 结果',
    `agent_end_time` DATETIME COMMENT '结束时间',
    `log_llm_agent_context_id` BIGINT COMMENT '关联 Agent 上下文 ID',
    `final_task_detail_id` BIGINT COMMENT '最终任务详情 ID',
    `create_by`     BIGINT NOT NULL COMMENT '创建人',
    `create_time`   DATETIME(3) COMMENT '创建时间',
    INDEX `idx_request_id` (`request_id`),
    INDEX `idx_agent_id` (`agent_id`),
    INDEX `idx_biz_id` (`biz_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 实例表';

-- ---------------------------------------------------------------------------
-- log_llm_task_detail: 任务详情表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `log_llm_task_detail` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    `log_llm_agent_main_id` BIGINT COMMENT '关联 Agent 实例 ID',
    `request_id`          VARCHAR(128) NOT NULL COMMENT '请求唯一 ID',
    `agent_name`          VARCHAR(128) COMMENT 'Agent 名称',
    `task_name`           VARCHAR(128) COMMENT '任务名称',
    `task_unique_name`    VARCHAR(256) COMMENT '任务唯一名称（定位源码行号）',
    `task_type`           VARCHAR(32) COMMENT '任务类型（expression/template/action等）',
    `task_index`          INT COMMENT '执行顺序',
    `full_path`           VARCHAR(512) COMMENT '完整路径（源码行号映射）',
    `success`             TINYINT(1) COMMENT '是否成功',
    `result`              TEXT COMMENT '执行结果 JSON',
    `error_message`       TEXT COMMENT '错误信息',
    `create_time`         DATETIME COMMENT '创建时间',
    `task_end_time`       DATETIME COMMENT '结束时间',
    INDEX `idx_request_id` (`request_id`),
    INDEX `idx_agent_name` (`agent_name`),
    INDEX `idx_task_index` (`task_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务详情表';

-- ---------------------------------------------------------------------------
-- log_llm_task_step: 任务步骤表（可选，用于步骤级分析）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `log_llm_task_step` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `log_llm_task_detail_id` BIGINT COMMENT '关联任务详情 ID',
    `step_index`      INT COMMENT '步骤序号',
    `step_type`       VARCHAR(64) COMMENT '步骤类型（template/rag/action等）',
    `input`           TEXT COMMENT '输入',
    `output`          TEXT COMMENT '输出',
    `create_time`     DATETIME COMMENT '创建时间',
    INDEX `idx_task_detail_id` (`log_llm_task_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务步骤表';

-- ---------------------------------------------------------------------------
-- log_llm_http_request: LLM HTTP 请求记录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `log_llm_http_request` (
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
    `request_id`        VARCHAR(128) NOT NULL COMMENT '请求唯一 ID',
    `agent_name`        VARCHAR(128) COMMENT 'Agent 名称',
    `task_name`         VARCHAR(128) COMMENT '任务名称',
    `provider`          VARCHAR(32) COMMENT 'LLM Provider（openai/deepseek等）',
    `model`             VARCHAR(64) COMMENT '模型名称',
    `prompt_tokens`     INT COMMENT 'Prompt Token 数',
    `completion_tokens` INT COMMENT 'Completion Token 数',
    `total_tokens`      INT COMMENT '总 Token 数',
    `latency_ms`       BIGINT COMMENT '延迟（毫秒）',
    `success_expression` TINYINT(1) COMMENT '是否成功（表达式计算结果）',
    `request_body`      TEXT COMMENT '请求体',
    `response_body`     TEXT COMMENT '响应体',
    `error_message`     TEXT COMMENT '错误信息',
    `create_time`       DATETIME COMMENT '创建时间',
    INDEX `idx_request_id` (`request_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM HTTP 请求记录表';

-- ---------------------------------------------------------------------------
-- 插入示例数据（用于测试）
-- ---------------------------------------------------------------------------
INSERT INTO `log_llm_agent_main`
    (`agent_id`, `request_id`, `biz_id`, `top_agent_name`, `is_entrance_agent`,
     `title`, `task_status`, `agent_status`, `success`, `create_by`,
     `create_time`, `agent_end_time`)
VALUES
    (1, 'req_test_001', 1, 'DataAnalysisAgent', b'1',
     'demo', 2, 2, b'1', 1,
     NOW(), DATE_ADD(NOW(), INTERVAL 30 SECOND)),
    (2, 'req_test_001', 1, 'SubSearchAgent', b'0',
     'demo', 2, 2, b'1', 1,
     NOW(), DATE_ADD(NOW(), INTERVAL 20 SECOND));

INSERT INTO `log_llm_task_detail`
    (`log_llm_agent_main_id`, `request_id`, `agent_name`, `task_name`, `task_unique_name`,
     `task_type`, `task_index`, `full_path`, `success`, `result`, `create_time`, `task_end_time`)
VALUES
    (1, 'req_test_001', 'DataAnalysisAgent', 'analyze', 'task_analyze',
     'expression', 0, '/root/analyze', 1, '{"status":"ok"}',
     NOW(), DATE_ADD(NOW(), INTERVAL 5 SECOND)),
    (1, 'req_test_001', 'DataAnalysisAgent', 'process', 'task_process',
     'expression', 1, '/root/process', 0, NULL,
     'NullPointerException at line 42',
     NOW(), DATE_ADD(NOW(), INTERVAL 10 SECOND));

INSERT INTO `log_llm_http_request`
    (`request_id`, `agent_name`, `task_name`, `provider`, `model`,
     `prompt_tokens`, `completion_tokens`, `total_tokens`, `latency_ms`,
     `success_expression`, `request_body`, `response_body`, `create_time`)
VALUES
    ('req_test_001', 'DataAnalysisAgent', 'analyze', 'openai', 'gpt-4o',
     1500, 320, 1820, 1200,
     1, '{"model":"gpt-4o","messages":[...]}', '{"choices":[...]}', NOW());

-- 创建只读用户（可选，生产环境推荐）
-- CREATE USER IF NOT EXISTS 'llm_agent_reader'@'%' IDENTIFIED BY 'llm_agent_reader_password';
-- GRANT SELECT ON `llm_agent`.* TO 'llm_agent_reader'@'%';
-- FLUSH PRIVILEGES;
