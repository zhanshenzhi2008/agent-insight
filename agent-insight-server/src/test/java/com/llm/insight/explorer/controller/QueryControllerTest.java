package com.llm.insight.explorer.controller;

import com.llm.insight.explorer.dto.QueryRequest;
import com.llm.insight.explorer.dto.QueryResponse;
import com.llm.insight.explorer.engine.QueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryControllerTest {

    private QueryController controller(QueryService queryService, MongoTemplate mongoTemplate) {
        return new QueryController(queryService, mongoTemplate);
    }

    private static QueryResponse.ColumnMeta colMeta(String key, String label) {
        return QueryResponse.ColumnMeta.builder().key(key).label(label).build();
    }

    private static QueryRequest.FilterCondition filter(String column, String operator, Object value) {
        return QueryRequest.FilterCondition.builder().column(column).operator(operator).value(value).build();
    }

    private static QueryRequest.SortCondition sort(String field, String direction) {
        return QueryRequest.SortCondition.builder().field(field).direction(direction).build();
    }

/**
 * Unit tests for {@link QueryController}.
 * Covers TC-EX-20 ~ TC-EX-32 (Data Explorer: dynamic query).
 */
class QueryControllerTest {

    private QueryController controller(QueryService queryService, MongoTemplate mongoTemplate) {
        return new QueryController(queryService, mongoTemplate);
    }

    // ─── TC-EX-20: 基础分页查询 ──────────────────────────────────────────────

    @Nested
    class ExecuteQuery {

        @Test
        @DisplayName("POST /api/v1/explorer/query/execute returns data + totalCount + columns")
        void testExecuteQuery() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(
                            java.util.Map.of("id", 1, "name", "Alice"),
                            java.util.Map.of("id", 2, "name", "Bob")
                    ))
                    .columns(List.of(colMeta("id", "ID"), colMeta("name", "Name")))
                    .total(2L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .page(0)
                    .pageSize(20)
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
            assertThat(resp.getData().getData()).hasSize(2);
            assertThat(resp.getData().getColumns()).hasSize(2);
            assertThat(resp.getData().getTotal()).isEqualTo(2L);
        }
    }

    // ─── TC-EX-21 ~ TC-EX-24: 条件过滤 ──────────────────────────────────────

    @Nested
    class QueryWithFilters {

        @Test
        @DisplayName("POST /execute with EQ filter returns filtered results")
        void testQueryWithEqFilter() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("id", 1, "status", "PENDING")))
                    .columns(List.of())
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("orders")
                    .filters(List.of(filter("status", "EQ", "PENDING")))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
            assertThat(resp.getData().getData()).hasSize(1);
        }

        @Test
        @DisplayName("POST /execute with LIKE filter returns fuzzy matches")
        void testQueryWithLikeFilter() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("id", 1, "name", "张三")))
                    .columns(List.of())
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .filters(List.of(filter("name", "LIKE", "张")))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
        }

        @Test
        @DisplayName("POST /execute with GT filter returns correct numeric comparison")
        void testQueryWithGtFilter() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("id", 2, "amount", 1500)))
                    .columns(List.of())
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("orders")
                    .filters(List.of(filter("amount", "GT", "1000")))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
        }

        @Test
        @DisplayName("POST /execute with multiple AND filters applies all conditions")
        void testQueryWithMultipleFilters() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok").data(List.of()).columns(List.of()).total(0L).build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("orders")
                    .filters(List.of(
                            filter("status", "EQ", "COMPLETED"),
                            filter("amount", "GT", "500")
                    ))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
        }
    }

    // ─── TC-EX-25: 排序 ────────────────────────────────────────────────────

    @Nested
    class QueryWithSorting {

        @Test
        @DisplayName("POST /execute with sort parameter returns sorted results")
        void testQueryWithSorting() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(
                            java.util.Map.of("id", 1, "created_at", "2025-01-01"),
                            java.util.Map.of("id", 2, "created_at", "2025-01-15")
                    ))
                    .columns(List.of())
                    .total(2L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("orders")
                    .orders(List.of(sort("created_at", "DESC")))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
        }
    }

    // ─── TC-EX-26: 指定列查询 ────────────────────────────────────────────────

    @Nested
    class QueryWithColumns {

        @Test
        @DisplayName("POST /execute with specific columns returns only those columns")
        void testQueryWithSpecificColumns() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("id", 1, "name", "Alice")))
                    .columns(List.of(colMeta("id", "ID"), colMeta("name", "Name")))
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .selectFields(List.of("id", "name"))
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
        }
    }

    // ─── TC-EX-27: 空 columns（全列）─────────────────────────────────────────

    @Nested
    class QueryWithEmptyColumns {

        @Test
        @DisplayName("POST /execute with empty columns array returns all columns")
        void testQueryEmptyColumns() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("id", 1, "name", "Alice", "email", "a@b.com")))
                    .columns(List.of(
                            colMeta("id", "ID"),
                            colMeta("name", "Name"),
                            colMeta("email", "Email")
                    ))
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .selectFields(List.of())
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
            assertThat(resp.getData().getColumns()).hasSize(3);
        }
    }

    // ─── TC-EX-31 & TC-EX-32: 分页边界 ─────────────────────────────────────

    @Nested
    class PaginationBoundaries {

        @Test
        @DisplayName("POST /execute with page=0 returns first page")
        void testPaginationPageZero() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok").data(List.of(java.util.Map.of("id", 1))).columns(List.of()).total(100L).build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .page(0)
                    .pageSize(20)
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTotal()).isEqualTo(100L);
        }

        @Test
        @DisplayName("POST /execute with page=999 returns empty data with correct totalCount")
        void testPaginationBeyondData() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok").data(List.of()).columns(List.of()).total(5L).build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("users")
                    .page(999)
                    .pageSize(20)
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getData()).isEmpty();
            assertThat(resp.getData().getTotal()).isEqualTo(5L);
        }
    }

    // ─── TC-EX-30: 数据源不存在 ─────────────────────────────────────────────

    @Nested
    class InvalidDatasource {

        @Test
        @DisplayName("POST /execute with invalid datasourceKey returns error status")
        void testQueryInvalidDatasource() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("error")
                    .error("数据源不存在: invalid_ds")
                    .data(List.of())
                    .columns(List.of())
                    .total(0L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("invalid_ds")
                    .tableName("users")
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(-1);
            assertThat(resp.getMessage()).isEqualTo("error");
            assertThat(resp.getData().getStatus()).isEqualTo("error");
        }
    }

    // ─── TC-EX-28: COUNT 查询 ───────────────────────────────────────────────

    @Nested
    class CountQuery {

        @Test
        @DisplayName("POST /execute returns correct totalCount for COUNT queries")
        void testCountQuery() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok").data(List.of()).columns(List.of()).total(42L).build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mysql")
                    .tableName("orders")
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTotal()).isEqualTo(42L);
        }
    }

    // ─── TC-EX-29: MongoDB 查询 ─────────────────────────────────────────────

    @Nested
    class MongoDBQuery {

        @Test
        @DisplayName("POST /execute with MONGODB datasource returns Document data")
        void testMongoDBQuery() {
            QueryService queryService = mock(QueryService.class);
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);

            QueryResponse response = QueryResponse.builder()
                    .status("ok")
                    .data(List.of(java.util.Map.of("_id", "obj-id", "name", "TestDoc")))
                    .columns(List.of(colMeta("name", "Name")))
                    .total(1L)
                    .build();

            when(queryService.execute(any(QueryRequest.class))).thenReturn(response);
            when(mongoTemplate.save(any())).thenReturn(null);

            QueryRequest request = QueryRequest.builder()
                    .datasourceKey("test_mongo")
                    .tableName("test_collection")
                    .build();

            var resp = controller(queryService, mongoTemplate).execute(request);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
            assertThat(resp.getData().getTotal()).isEqualTo(1L);
        }
    }
}
