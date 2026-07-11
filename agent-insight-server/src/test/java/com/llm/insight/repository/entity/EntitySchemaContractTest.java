package com.llm.insight.repository.entity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 防御性契约：{@code @Entity} 上 {@code @Column(name = ...)} 声明的列，必须真实存在于
 * 对应业务表的 MySQL {@code llm_agent} 库里。否则任何一次 SELECT 都会 500，
 * 前端 Vite proxy 把 500 转成 502，从而整页炸掉。
 *
 * <p>背景：
 * <ul>
 *   <li>{@code docs/00-revision-2026-07-03.md} 决策 #2：本工程代码绝不触碰 llm-agent 工程，
 *       因此实体声明的列必须以 llm-agent 的真实表为准。</li>
 *   <li>AGENTS.md §6.1：禁止动态拼接 SQL；所有 SQL 必须可静态校验。</li>
 *   <li>真实故障（2026-07-06）：{@code LogLlmAgentMain.chatMessageId} 映射到了
 *       不存在的 {@code chat_message_id} 列，导致
 *       {@code GET /api/v1/requests} 整体 500 → 前端 502，
 *       <code>RequestSearchPage</code> 整页渲染失败。</li>
 * </ul>
 *
 * <p>当前范围：本测试覆盖 <b>读路径上确实会被查</b> 的实体
 * （{@code log_llm_agent_main} / {@code log_llm_task_detail}）。
 * 另两个实体（{@code log_llm_task_step}、{@code log_llm_http_request}）
 * 存在结构性 drift（表已废弃、列名漂移），属于另一个独立修复，
 * 见 {@code docs/05-任务排期.md} W4 老 Service 切到 MongoQueryEngine。
 */
class EntitySchemaContractTest {

    private static final String SCHEMA = "llm_agent";

    @Test
    @Disabled("需要真实 MySQL 连接，CI 环境跳过；本地开发时运行")
    @DisplayName("读路径实体的 @Column 必须真实存在于 MySQL (防止 502 雪崩)")
    void readPathEntitiesMustMatchMysqlSchema() throws Exception {
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("MYSQL_PORT", "3306");
        String user = System.getenv().getOrDefault("MYSQL_USERNAME",
                System.getenv().getOrDefault("MYSQL_USER", "root"));
        String pass = System.getenv().getOrDefault("MYSQL_PASSWORD", "root830i");
        String url = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, SCHEMA);

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {

            List<String> missing = new ArrayList<>();
            for (Class<?> entity : readPathEntities()) {
                Table table = entity.getAnnotation(Table.class);
                String tableName = table.name();
                Set<String> actualColumns = fetchColumns(conn, SCHEMA, tableName);

                for (String column : declaredColumns(entity)) {
                    if (!actualColumns.contains(column.toLowerCase())) {
                        missing.add(entity.getSimpleName() + "." + column
                                + " → 表 " + tableName + " 缺列");
                    }
                }
            }

            assertThat(missing)
                    .withFailMessage(() -> "以下实体字段在 MySQL 中不存在，请删除对应 @Column：\n  - "
                            + String.join("\n  - ", missing))
                    .isEmpty();
        }
    }

    private List<Class<?>> readPathEntities() {
        return List.of(
                LogLlmAgentMain.class,
                LogLlmTaskDetail.class);
    }

    private Set<String> declaredColumns(Class<?> entity) {
        Set<String> cols = new HashSet<>();
        for (Field f : entity.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            Column col = f.getAnnotation(Column.class);
            if (col != null && !col.name().isEmpty()) {
                cols.add(col.name().toLowerCase());
            }
        }
        return cols;
    }

    private Set<String> fetchColumns(Connection conn, String schema, String table) throws Exception {
        Set<String> cols = new HashSet<>();
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString(1).toLowerCase());
                }
            }
        }
        return cols;
    }
}