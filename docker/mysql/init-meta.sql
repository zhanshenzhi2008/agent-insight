-- =============================================================================
-- Agent Insight 元数据表初始化脚本（agent-insight 自身业务表）
-- 首次启动 docker-compose 时自动执行（按文件名排序在 log_llm 业务表之后）
--
-- 内容：
--   1. insight_ai_vendor      AI 模型供应商凭证表（一个 vendor 唯一一条）
--   2. insight_model_instance  AI 模型实例表（按 capability/tier 路由）
--   3. 数据迁移：旧的 insight_ai_model → 新两张表（vendor 维度拆出凭证）
--
-- 设计要点：
--   - vendor 凭证集中（base_url / token / api_version / proxy / timeout 等）
--   - 模型实例按 (vendor, model_name, capability) 唯一
--   - 调用参数 (max_tokens / temperature / top_p) 跟模型走
--   - is_current=1 在 (capability, tier) 维度唯一（路由时优先选）
-- =============================================================================

USE `agent_insight`;

-- ---------------------------------------------------------------------------
-- 1. insight_ai_vendor：AI 模型供应商凭证表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `insight_ai_vendor` (
    `id`                BIGINT PRIMARY KEY,
    `vendor`            VARCHAR(64) NOT NULL COMMENT '供应商标识（openai/anthropic/azure/ollama/deepseek/google-genai 等）',
    `display_name`      VARCHAR(128) COMMENT '前端展示名',
    `base_url`          VARCHAR(512) COMMENT 'API 端点',
    `token_encrypted`   VARBINARY(2048) COMMENT 'AES-256-GCM 加密后的 API Key',
    `api_version`       VARCHAR(64) COMMENT 'Azure 专用，例如 2024-02-15',
    `proxy_host`        VARCHAR(128) COMMENT 'HTTP 代理主机',
    `proxy_port`        INT COMMENT 'HTTP 代理端口',
    `timeout_seconds`   INT NOT NULL DEFAULT 30 COMMENT '单次请求超时',
    `max_retries`       INT NOT NULL DEFAULT 3 COMMENT '失败重试次数',
    `extra_config`      TEXT COMMENT 'JSON 扩展配置（厂商特有字段）',
    `status`            TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    `description`       VARCHAR(512) COMMENT '备注',
    `create_time`       DATETIME(3) NOT NULL,
    `update_time`       DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_vendor` (`vendor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型供应商凭证表';

-- ---------------------------------------------------------------------------
-- 2. insight_model_instance：AI 模型实例表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `insight_model_instance` (
    `id`                BIGINT PRIMARY KEY,
    `vendor_id`         BIGINT NOT NULL COMMENT 'FK → insight_ai_vendor.id',
    `model_name`        VARCHAR(128) NOT NULL COMMENT '模型名（gpt-4o / claude-3.5-sonnet 等）',
    `deployment_name`   VARCHAR(128) COMMENT 'Azure 专用 deployment 名',
    `capability`        VARCHAR(32) NOT NULL COMMENT 'CHAT / EMBEDDING / VISION / TTS / RERANKER',
    `tier`              VARCHAR(32) NOT NULL COMMENT 'PRODUCTION / LIGHT / EXPERIMENTAL',
    `priority`          INT NOT NULL DEFAULT 1 COMMENT '同 tier 内越小越优先',
    `max_tokens`        INT COMMENT '单次响应最大 token 数',
    `temperature`       DECIMAL(3,2) COMMENT '0.00 ~ 2.00',
    `top_p`             DECIMAL(3,2) COMMENT '0.00 ~ 1.00',
    `is_active`         TINYINT NOT NULL DEFAULT 1 COMMENT '总开关 0=禁用 1=启用',
    `is_current`        TINYINT NOT NULL DEFAULT 0 COMMENT '同 (capability,tier) 下唯一标记',
    `description`       VARCHAR(512) COMMENT '备注',
    `create_time`       DATETIME(3) NOT NULL,
    `update_time`       DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_vendor_model_cap` (`vendor_id`, `model_name`, `capability`),
    INDEX `idx_cap_tier_active` (`capability`, `tier`, `is_active`),
    INDEX `idx_cap_tier_current` (`capability`, `tier`, `is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型实例表';

-- ---------------------------------------------------------------------------
-- 3. 数据迁移：insight_ai_model → insight_ai_vendor + insight_model_instance
-- 仅在旧表存在且新表为空时执行（幂等）
-- ---------------------------------------------------------------------------

-- 3.1 迁移 vendor 凭证
INSERT INTO `insight_ai_vendor` (
    `id`, `vendor`, `display_name`, `base_url`, `token_encrypted`,
    `api_version`, `proxy_host`, `proxy_port`,
    `timeout_seconds`, `max_retries`, `extra_config`,
    `status`, `description`, `create_time`, `update_time`
)
SELECT
    m.`id`,
    m.`vendor`,
    m.`vendor` AS `display_name`,
    m.`base_url`,
    m.`token_encrypted`,
    NULL AS `api_version`,
    NULL AS `proxy_host`,
    NULL AS `proxy_port`,
    30 AS `timeout_seconds`,
    3 AS `max_retries`,
    NULL AS `extra_config`,
    m.`status`,
    m.`description`,
    m.`create_time`,
    m.`update_time`
FROM `insight_ai_model` m
WHERE NOT EXISTS (SELECT 1 FROM `insight_ai_vendor` v WHERE v.`vendor` = m.`vendor`);

-- 3.2 为每个旧 model 创建一行 model_instance（默认 capability=CHAT, tier=LIGHT）
--     priority 取 id 末位，temperature 沿用，max_tokens/top_p 留空
INSERT INTO `insight_model_instance` (
    `id`, `vendor_id`, `model_name`, `deployment_name`,
    `capability`, `tier`, `priority`,
    `max_tokens`, `temperature`, `top_p`,
    `is_active`, `is_current`,
    `description`, `create_time`, `update_time`
)
SELECT
    (m.`id` * 10 + 1) AS `id`,
    v.`id` AS `vendor_id`,
    TRIM(SUBSTRING_INDEX(m.`models`, ',', 1)) AS `model_name`,
    NULL AS `deployment_name`,
    'CHAT' AS `capability`,
    CASE WHEN m.`status` = 1 THEN 'PRODUCTION' ELSE 'LIGHT' END AS `tier`,
    1 AS `priority`,
    NULL AS `max_tokens`,
    m.`temperature`,
    NULL AS `top_p`,
    m.`status` AS `is_active`,
    CASE WHEN m.`status` = 1 THEN 1 ELSE 0 END AS `is_current`,
    CONCAT('从 insight_ai_model id=', m.`id`, ' 迁移') AS `description`,
    m.`create_time`,
    m.`update_time`
FROM `insight_ai_model` m
JOIN `insight_ai_vendor` v ON v.`vendor` = m.`vendor`
WHERE NOT EXISTS (
    SELECT 1 FROM `insight_model_instance` i
    WHERE i.`vendor_id` = v.`id`
      AND i.`model_name` = TRIM(SUBSTRING_INDEX(m.`models`, ',', 1))
      AND i.`capability` = 'CHAT'
);

-- 3.3 处理旧 models 字段里的逗号分隔多模型（拆分后追加）
-- 例：openai 的 models = "gpt-4o,gpt-4o-mini,text-embedding-3-large"
-- 第二个起作为 capability=CHAT tier=LIGHT 的附加模型
INSERT INTO `insight_model_instance` (
    `id`, `vendor_id`, `model_name`, `deployment_name`,
    `capability`, `tier`, `priority`,
    `max_tokens`, `temperature`, `top_p`,
    `is_active`, `is_current`,
    `description`, `create_time`, `update_time`
)
SELECT
    (m.`id` * 10 + n.n + 1) AS `id`,
    v.`id` AS `vendor_id`,
    TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(m.`models`, ',', n.n + 2), ',', -1)) AS `model_name`,
    NULL AS `deployment_name`,
    'CHAT' AS `capability`,
    'LIGHT' AS `tier`,
    (n.n + 2) AS `priority`,
    NULL AS `max_tokens`,
    NULL AS `top_p`,
    m.`status` AS `is_active`,
    0 AS `is_current`,
    CONCAT('从 insight_ai_model id=', m.`id`, ' 迁移（多模型）') AS `description`,
    m.`create_time`,
    m.`update_time`
FROM `insight_ai_model` m
JOIN `insight_ai_vendor` v ON v.`vendor` = m.`vendor`
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) n ON n.n < (CHAR_LENGTH(m.`models`) - CHAR_LENGTH(REPLACE(m.`models`, ',', '')))
WHERE NOT EXISTS (
    SELECT 1 FROM `insight_model_instance` i
    WHERE i.`vendor_id` = v.`id`
      AND i.`model_name` = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(m.`models`, ',', n.n + 2), ',', -1))
      AND i.`capability` = 'CHAT'
);

-- ---------------------------------------------------------------------------
-- 4. 验证：迁移完成后给出统计（便于运维确认）
-- ---------------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM `insight_ai_vendor`) AS vendor_count,
    (SELECT COUNT(*) FROM `insight_model_instance`) AS instance_count,
    (SELECT COUNT(*) FROM `insight_ai_model`) AS legacy_count;