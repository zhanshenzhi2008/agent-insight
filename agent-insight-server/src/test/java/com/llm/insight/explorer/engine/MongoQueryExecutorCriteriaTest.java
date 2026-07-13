package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.dto.QueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MongoQueryExecutor#buildCriteria(QueryRequest.FilterCondition)}.
 * <p>
 * 纯函数测试（无 MongoDB 依赖），覆盖 12 个操作符 + 字段白名单 + 边界场景。
 * 文档: docs/00-revision-2026-07-03.md §3 (W2 - 引擎骨架)
 */
class MongoQueryExecutorCriteriaTest {

    private final MongoQueryExecutor executor =
            new MongoQueryExecutor(null);  // buildCriteria 不调 MongoTemplate，构造不依赖

    // ------------------------------------------------------------------------
    // EQ / NE / 比较类
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("EQ → Criteria.where(f).is(v)")
    void eq() {
        Criteria c = executor.buildCriteria(filter("status", "EQ", "PAID"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"status\"", "\"PAID\"");
    }

    @Test
    @DisplayName("NE → Criteria.where(f).ne(v)")
    void ne() {
        Criteria c = executor.buildCriteria(filter("status", "NE", "PAID"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"status\"", "\"$ne\"", "\"PAID\"");
    }

    @Test
    @DisplayName("GT / GTE / LT / LTE 各自产生对应操作符")
    void rangeOperators() {
        assertThat(executor.buildCriteria(filter("amount", "GT",  100)).getCriteriaObject().toJson())
                .contains("\"$gt\"");
        assertThat(executor.buildCriteria(filter("amount", "GTE", 100)).getCriteriaObject().toJson())
                .contains("\"$gte\"");
        assertThat(executor.buildCriteria(filter("amount", "LT",  100)).getCriteriaObject().toJson())
                .contains("\"$lt\"");
        assertThat(executor.buildCriteria(filter("amount", "LTE", 100)).getCriteriaObject().toJson())
                .contains("\"$lte\"");
    }

    // ------------------------------------------------------------------------
    // LIKE / IN / NOT_IN
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("LIKE → 大小写不敏感的正则匹配（Spring 5.1+ 用 $regularExpression）")
    void like() {
        Criteria c = executor.buildCriteria(filter("name", "LIKE", "ali"));
        // Spring Data 4.x/5.x 重命名为 $regularExpression（兼容 $regex）
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"name\"", "\"ali\"")
                .containsAnyOf("\"$regex\"", "\"$regularExpression\"");
    }

    @Test
    @DisplayName("IN → Criteria.where(f).in([v1, v2, ...])")
    void in() {
        Criteria c = executor.buildCriteria(filter("status", "IN", List.of("A", "B", "C")));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"status\"", "\"$in\"", "\"A\"", "\"B\"", "\"C\"");
    }

    @Test
    @DisplayName("NOT_IN → Criteria.where(f).nin([...])")
    void notIn() {
        Criteria c = executor.buildCriteria(filter("status", "NOT_IN", List.of("X")));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"$nin\"");
    }

    // ------------------------------------------------------------------------
    // NULL 系列
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("IS_NULL → Criteria.where(f).isNull()")
    void isNull() {
        Criteria c = executor.buildCriteria(filterNull("deleted_at", "IS_NULL"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"deleted_at\"", "null");
    }

    @Test
    @DisplayName("IS_NOT_NULL → Criteria.where(f).ne(null)")
    void isNotNull() {
        Criteria c = executor.buildCriteria(filterNull("email", "IS_NOT_NULL"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"email\"", "\"$ne\"", "null");
    }

    // ------------------------------------------------------------------------
    // BETWEEN
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("BETWEEN → 同时含 $gte 和 $lte")
    void between() {
        Criteria c = executor.buildCriteria(
                QueryRequest.FilterCondition.builder()
                        .column("amount").operator("BETWEEN")
                        .value(100).value2(500).build());
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"amount\"", "\"$gte\"", "\"$lte\"");
    }

    // ------------------------------------------------------------------------
    // 未知操作符默认行为（兜底为 EQ）
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("未知操作符 → 降级为 EQ（业务方写错不至于 500）")
    void unknownOperatorFallsBackToEq() {
        Criteria c = executor.buildCriteria(filter("x", "BOGUS_OP", "v"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"x\"", "\"v\"");
    }

    // ------------------------------------------------------------------------
    // 通配字段名（合法 Mongo 字段名）
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("camelCase 字段名（与 llm-agent Mongo 真实字段对齐）")
    void camelCaseFieldName() {
        Criteria c = executor.buildCriteria(filter("requestId", "EQ", "abc"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"requestId\"", "\"abc\"");
    }

    @Test
    @DisplayName("snake_case 字段名（兼容老 MySQL 字段命名）")
    void snakeCaseFieldName() {
        Criteria c = executor.buildCriteria(filter("request_id", "EQ", "abc"));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"request_id\"");
    }

    // ------------------------------------------------------------------------
    // 防御性：value 是数组
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("IN: value 是数组也能正确传递（来自前端直传）")
    void inWithArrayValue() {
        Criteria c = executor.buildCriteria(filter("tags", "IN", new String[]{"a", "b"}));
        assertThat(c.getCriteriaObject().toJson())
                .contains("\"tags\"", "\"$in\"", "\"a\"", "\"b\"");
    }

    // ------------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------------

    private QueryRequest.FilterCondition filter(String col, String op, Object value) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator(op).value(value).build();
    }

    private QueryRequest.FilterCondition filterNull(String col, String op) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator(op).build();
    }
}
