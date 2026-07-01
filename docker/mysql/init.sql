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
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `log_llm_agent_main` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `agent_id`      VARCHAR(64) NOT NULL COMMENT 'Agent 实例 ID',
    `request_id`    VARCHAR(128) NOT NULL COMMENT '请求唯一 ID',
    `agent_name`    VARCHAR(128) COMMENT 'Agent 名称（显示用）',
    `top_agent_name` VARCHAR(128) COMMENT '顶层 Agent 名称',
    `entrance_agent` TINYINT(1) DEFAULT 0 COMMENT '是否入口 Agent',
    `task_status`   INT COMMENT '任务状态（0=pending, 1=running, 2=success, 3=failed）',
    `agent_status`  INT COMMENT 'Agent 状态（1=active, 2=completed）',
    `success`       TINYINT(1) COMMENT '是否成功',
    `create_time`   DATETIME COMMENT '创建时间',
    `update_time`   DATETIME COMMENT '更新时间',
    `agent_end_time` DATETIME COMMENT '结束时间',
    INDEX `idx_request_id` (`request_id`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_top_agent_name` (`top_agent_name`)
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
    (`agent_id`, `request_id`, `agent_name`, `top_agent_name`, `entrance_agent`,
     `task_status`, `agent_status`, `success`, `create_time`, `update_time`, `agent_end_time`)
VALUES
    ('agent_001', 'req_test_001', 'DataAnalysisAgent', 'DataAnalysisAgent', 1,
     2, 2, 1, NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 30 SECOND)),
    ('agent_002', 'req_test_001', 'SubSearchAgent', 'DataAnalysisAgent', 0,
     2, 2, 1, NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 20 SECOND));

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
