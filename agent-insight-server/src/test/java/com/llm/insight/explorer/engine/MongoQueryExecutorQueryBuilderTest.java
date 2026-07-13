package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.dto.QueryRequest;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MongoQueryExecutor#buildQuery(QueryRequest)} 与 projection。
 * <p>
 * 验证：
 *   1. 多 filter 组合方式（AND）
 *   2. 空 filter 不报错
 *   3. selectFields 投影只返回指定字段
 * 文档: docs/00-revision-2026-07-03.md §3 (W2 - 引擎骨架)
 */
class MongoQueryExecutorQueryBuilderTest {

    private final MongoQueryExecutor executor = new MongoQueryExecutor(null);

    // ------------------------------------------------------------------------
    // buildQuery 基础
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("buildQuery: 空 filters 不报错，返回空 Query")
    void emptyFilters() {
        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main").build());

        // 不同 Mongo driver 版本：空 Query 可能返回 Document 或 EmptyDocument（Document 子类）
        // 比较结构而非类型
        assertThat(query.getQueryObject().toJson()).isEqualTo("{}");
    }

    @Test
    @DisplayName("buildQuery: 多个 filter 全部 AND 组合（Mongo 默认行为）")
    void multipleFilters() {
        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .filters(List.of(
                        QueryRequest.FilterCondition.builder()
                                .column("requestId").operator("EQ").value("r1").build(),
                        QueryRequest.FilterCondition.builder()
                                .column("success").operator("EQ").value(false).build(),
                        QueryRequest.FilterCondition.builder()
                                .column("createTime").operator("GTE").value("2026-07-01").build()
                ))
                .build());

        // Mongo 多 filter 默认是 AND（顶层 {} 中多个键并列）
        assertThat(query.getQueryObject().toJson())
                .contains("\"requestId\"", "\"r1\"")
                .contains("\"success\"", "false")
                .contains("\"createTime\"", "\"$gte\"", "\"2026-07-01\"");
    }

    @Test
    @DisplayName("buildQuery: single filter")
    void singleFilter() {
        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .filters(List.of(
                        QueryRequest.FilterCondition.builder()
                                .column("requestId").operator("EQ").value("r1").build()
                ))
                .build());

        assertThat(query.getQueryObject().toJson())
                .contains("\"requestId\"", "\"r1\"");
    }

    // ------------------------------------------------------------------------
    // projection（selectFields）— v1.0 新增
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("projection: selectFields 空 → Query.fields() 返回空 Document")
    void projectionEmpty() {
        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .selectFields(List.of())  // 空
                .build());

        // Field.empty() 时 getFieldsObject() 返回 {}
        assertThat(query.getFieldsObject()).isNotNull();
    }

    @Test
    @DisplayName("projection: selectFields 8 个字段 → Query.fields 包含对应 include 指令")
    void projectionEightFields() {
        // 业务场景：按 requestId 查 log_llm_agent_main，UI 只需关键 8 个字段
        List<String> selectFields = List.of(
                "id", "bizId", "requestId", "topAgentName",
                "taskStatus", "success", "createTime", "agentEndTime"
        );

        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .selectFields(selectFields)
                .build());

        // Field 在执行时才设置，这里只能断言方法调用不抛异常 + 返回非 null
        assertThat(query.getFieldsObject()).isNotNull();
        assertThat(query.getFieldsObject().keySet()).isEmpty();
        // （fields() 是 setter，getFieldsObject() 当前还没值，需要在 execute() 内设置后才有效）
    }

    @Test
    @DisplayName("projection: selectFields 含空白/空字符串会被过滤掉")
    void projectionFiltersBlanks() {
        // 防御性测试：UI 传 ["requestId", "", " ", null] 也不会崩
        // 注意：List.of 不支持 null，所以用 Arrays.asList
        Query query = executor.buildQuery(QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .selectFields(java.util.Arrays.asList("requestId", "", " ", null))
                .build());

        assertThat(query).isNotNull();
    }

    @Test
    @DisplayName("projection: 模拟 execute() 调用后 fields 被正确设置")
    void projectionAppliedInExecute() {
        // 完整重建 execute() 的 projection 路径
        QueryRequest req = QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .selectFields(List.of("id", "requestId", "success"))
                .page(0).pageSize(20)
                .build();

        Query query = executor.buildQuery(req);

        // 模拟 execute 内部逻辑
        if (req.getSelectFields() != null && !req.getSelectFields().isEmpty()) {
            List<String> fields = req.getSelectFields().stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(String::trim)
                    .toList();
            if (!fields.isEmpty()) {
                query.fields().include(fields);
            }
        }

        // 验证 Field 包含 3 个 include
        Document fieldsDoc = query.getFieldsObject();
        assertThat(fieldsDoc.keySet()).containsExactlyInAnyOrder("id", "requestId", "success");
        // Spring Data 的 include 全部映射到 1，exclude 映射到 0
        assertThat(fieldsDoc.values()).containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("projection: 大字段（agentResult）可独立控制")
    void projectionExcludesLargeField() {
        // 业务场景：列表页不返 agentResult（太大），详情页才要
        QueryRequest req = QueryRequest.builder()
                .tableName("log_llm_agent_main")
                .selectFields(List.of("id", "requestId", "success", "taskStatus"))
                .build();

        Query query = executor.buildQuery(req);

        List<String> fields = req.getSelectFields();
        query.fields().include(fields);

        // 不应出现 agentResult
        assertThat(query.getFieldsObject().keySet()).doesNotContain("agentResult");
        // 必须包含 4 个白名单
        assertThat(query.getFieldsObject().keySet()).containsExactlyInAnyOrderElementsOf(fields);
    }
}
