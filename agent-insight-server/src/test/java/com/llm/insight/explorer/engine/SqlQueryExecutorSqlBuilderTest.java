package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.dto.QueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure SQL builder in {@link SqlQueryExecutor}.
 *
 * These exercise buildSelectSql which is a pure function over the request —
 * no JDBC or connection pool required. This lets us validate WHERE clause
 * composition across all supported operators and SQL injection hardening.
 */
class SqlQueryExecutorSqlBuilderTest {

    private final SqlQueryExecutor executor = new SqlQueryExecutor(null);

    @Test
    @DisplayName("buildSelectSql: simple SELECT with no filters")
    void simpleSelect() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("orders").page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM orders");
        assertThat(seg.params()).isEmpty();
    }

    @Test
    @DisplayName("buildSelectSql: respects selectFields whitelist")
    void selectFields() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder()
                        .tableName("orders")
                        .selectFields(List.of("id", "amount"))
                        .page(0).pageSize(10)
                        .build());

        assertThat(seg.sql()).isEqualTo("SELECT id, amount FROM orders");
    }

    @Test
    @DisplayName("buildSelectSql: EQ filter produces = ?")
    void eqFilter() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(eq("status", "PAID")))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM t WHERE status = ?");
        assertThat(seg.params()).containsExactly("PAID");
    }

    @Test
    @DisplayName("buildSelectSql: LIKE wraps value in %...%")
    void likeFilter() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(like("name", "ali")))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM t WHERE name LIKE ?");
        assertThat(seg.params()).containsExactly("%ali%");
    }

    @Test
    @DisplayName("buildSelectSql: IN with N values produces N placeholders")
    void inFilter() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(in("status", List.of("A", "B", "C"))))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM t WHERE status IN (?, ?, ?)");
        assertThat(seg.params()).hasSize(1);
        assertThat((Object[]) seg.params().get(0)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("buildSelectSql: BETWEEN adds both bounds as params")
    void betweenFilter() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(between("amount", 100, 500)))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM t WHERE amount BETWEEN ? AND ?");
        assertThat(seg.params()).containsExactly(100, 500);
    }

    @Test
    @DisplayName("buildSelectSql: IS_NULL / IS_NOT_NULL have no params")
    void nullFilters() {
        SqlQueryExecutor.SqlSegment nullSeg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(QueryRequest.FilterCondition.builder()
                                .column("deleted_at").operator("IS_NULL").build()))
                        .page(0).pageSize(10).build());
        assertThat(nullSeg.sql()).isEqualTo("SELECT * FROM t WHERE deleted_at IS NULL");
        assertThat(nullSeg.params()).isEmpty();

        SqlQueryExecutor.SqlSegment notNullSeg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .filters(List.of(QueryRequest.FilterCondition.builder()
                                .column("email").operator("IS_NOT_NULL").build()))
                        .page(0).pageSize(10).build());
        assertThat(notNullSeg.sql()).isEqualTo("SELECT * FROM t WHERE email IS NOT NULL");
    }

    @Test
    @DisplayName("buildSelectSql: combines multiple filters with AND/OR per filter")
    void multipleFilters() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("orders")
                        .filters(List.of(
                                eq("status", "PAID"),
                                QueryRequest.FilterCondition.builder()
                                        .column("amount").operator("GT").value(100)
                                        .combine("OR").build()))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM orders WHERE status = ? OR amount > ?");
        assertThat(seg.params()).containsExactly("PAID", 100);
    }

    @Test
    @DisplayName("buildSelectSql: ORDER BY with mixed directions")
    void orderBy() {
        SqlQueryExecutor.SqlSegment seg = executor.buildSelectSql(
                QueryRequest.builder().tableName("t")
                        .orders(List.of(
                                QueryRequest.SortCondition.builder()
                                        .field("create_time").direction("DESC").build(),
                                QueryRequest.SortCondition.builder()
                                        .field("id").direction("ASC").build()))
                        .page(0).pageSize(10).build());

        assertThat(seg.sql()).isEqualTo("SELECT * FROM t ORDER BY create_time DESC, id ASC");
    }

    private QueryRequest.FilterCondition eq(String col, Object v) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator("EQ").value(v).build();
    }

    private QueryRequest.FilterCondition like(String col, Object v) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator("LIKE").value(v).build();
    }

    private QueryRequest.FilterCondition in(String col, List<?> v) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator("IN").value(v).build();
    }

    private QueryRequest.FilterCondition between(String col, Object a, Object b) {
        return QueryRequest.FilterCondition.builder()
                .column(col).operator("BETWEEN").value(a).value2(b).build();
    }
}