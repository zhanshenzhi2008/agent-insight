-- =============================================================================
-- Agent Insight — MySQL 初始化脚本
-- 用途：CI 环境建表，docker-entrypoint-initdb.d 自动执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS `insight_datasource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `type` VARCHAR(20) NOT NULL COMMENT '类型：mysql/mongodb/postgres',
  `jdbc_url` VARCHAR(500) NOT NULL COMMENT 'JDBC URL',
  `username` VARCHAR(100) DEFAULT NULL COMMENT '用户名',
  `password` VARCHAR(200) DEFAULT NULL COMMENT '密码',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-否，1-是',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置表';

CREATE TABLE IF NOT EXISTS `insight_table_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `datasource_id` BIGINT NOT NULL COMMENT '数据源ID',
  `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
  `display_name` VARCHAR(200) DEFAULT NULL COMMENT '显示名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表配置表';

CREATE TABLE IF NOT EXISTS `insight_column_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `table_config_id` BIGINT NOT NULL COMMENT '表配置ID',
  `column_name` VARCHAR(100) NOT NULL COMMENT '列名',
  `display_name` VARCHAR(200) DEFAULT NULL COMMENT '显示名称',
  `column_type` VARCHAR(50) DEFAULT NULL COMMENT '列类型',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `display_order` INT DEFAULT 0 COMMENT '显示顺序',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_table_config_id` (`table_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列配置表';

-- ---------------------------------------------------------------------------
-- AI 模型供应商凭证表（agent-insight 自建业务表）
-- 与 docker/mysql/init-meta.sql 保持一致
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `insight_ai_vendor` (
    `id`                BIGINT PRIMARY KEY,
    `vendor`            VARCHAR(64) NOT NULL,
    `display_name`      VARCHAR(128),
    `base_url`          VARCHAR(512),
    `token_encrypted`   VARBINARY(2048),
    `api_version`       VARCHAR(64),
    `proxy_host`        VARCHAR(128),
    `proxy_port`        INT,
    `timeout_seconds`   INT NOT NULL DEFAULT 30,
    `max_retries`       INT NOT NULL DEFAULT 3,
    `extra_config`      TEXT,
    `status`            TINYINT NOT NULL DEFAULT 1,
    `description`       VARCHAR(512),
    `create_time`       DATETIME(3) NOT NULL,
    `update_time`       DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_vendor` (`vendor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型供应商凭证表';

CREATE TABLE IF NOT EXISTS `insight_model_instance` (
    `id`                BIGINT PRIMARY KEY,
    `vendor_id`         BIGINT NOT NULL,
    `model_name`        VARCHAR(128) NOT NULL,
    `deployment_name`   VARCHAR(128),
    `capability`        VARCHAR(32) NOT NULL,
    `tier`              VARCHAR(32) NOT NULL,
    `priority`          INT NOT NULL DEFAULT 1,
    `max_tokens`        INT,
    `temperature`       DECIMAL(3,2),
    `top_p`             DECIMAL(3,2),
    `is_active`         TINYINT NOT NULL DEFAULT 1,
    `is_current`        TINYINT NOT NULL DEFAULT 0,
    `description`       VARCHAR(512),
    `create_time`       DATETIME(3) NOT NULL,
    `update_time`       DATETIME(3) NOT NULL,
    UNIQUE KEY `uk_vendor_model_cap` (`vendor_id`, `model_name`, `capability`),
    INDEX `idx_cap_tier_active` (`capability`, `tier`, `is_active`),
    INDEX `idx_cap_tier_current` (`capability`, `tier`, `is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型实例表';
