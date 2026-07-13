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
