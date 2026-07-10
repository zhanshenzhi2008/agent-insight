package com.llm.insight.explorer.engine;

import com.llm.insight.explorer.document.InsightColumnConfig;
import com.llm.insight.explorer.document.InsightDatasource;
import com.llm.insight.explorer.document.InsightDatasource.ConnectionConfig;
import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import com.llm.insight.explorer.service.ConfigService;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ColumnAnalyzerService}.
 *
 * Covers the 2026-07-07 fix: empty Mongo collection must NOT silently return [].
 *   - fallback to insight_column_config when configured
 *   - throw IllegalStateException with actionable hint when neither docs nor config
 */
class ColumnAnalyzerServiceTest {

    private final DynamicDatasourceManager dsManager = mock(DynamicDatasourceManager.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);

    private final ColumnAnalyzerService analyzer = new ColumnAnalyzerService(dsManager, configService);

    private InsightDatasource mongoDs() {
        return InsightDatasource.builder()
                .datasourceKey("local-llm_agent")
                .datasourceName("test")
                .datasourceType("MONGODB")
                .connectionConfig(ConnectionConfig.builder()
                        .host("localhost").port(27017).database("llm_agent")
                        .build())
                .build();
    }

    @Test
    @DisplayName("Mongo 集合为空 + 无字段配置 → 抛出 IllegalStateException 引导用户")
    void mongoEmpty_noConfig_throws() {
        when(dsManager.getDatasource("local-llm_agent")).thenReturn(mongoDs());
        when(dsManager.getMongoTemplate(any(InsightDatasource.class))).thenReturn(mongo);
        when(mongo.find(any(Query.class), eq(Document.class), eq("log_llm_task_detail")))
                .thenReturn(List.of());
        when(configService.getColumnConfigs("local-llm_agent", "log_llm_task_detail"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> analyzer.analyze("local-llm_agent", "log_llm_task_detail"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-llm_agent")
                .hasMessageContaining("log_llm_task_detail")
                .hasMessageContaining("0 条文档");
    }

    @Test
    @DisplayName("Mongo 集合为空 + 有字段配置 → fallback 到 insight_column_config 返回推断列")
    void mongoEmpty_hasConfig_fallback() {
        when(dsManager.getDatasource("local-llm_agent")).thenReturn(mongoDs());
        when(dsManager.getMongoTemplate(any(InsightDatasource.class))).thenReturn(mongo);
        when(mongo.find(any(Query.class), eq(Document.class), eq("log_llm_task_detail")))
                .thenReturn(List.of());

        InsightColumnConfig col = InsightColumnConfig.builder()
                .columnName("taskIndex")
                .displayName("Task 序号")
                .dataType("INT")
                .renderType("TEXT")
                .build();
        when(configService.getColumnConfigs("local-llm_agent", "log_llm_task_detail"))
                .thenReturn(List.of(col));

        List<AnalyzedColumn> result = analyzer.analyze("local-llm_agent", "log_llm_task_detail");

        assertThat(result).hasSize(1);
        AnalyzedColumn a = result.get(0);
        assertThat(a.getColumnName()).isEqualTo("taskIndex");
        assertThat(a.getDisplayName()).isEqualTo("Task 序号");
        assertThat(a.getDataType()).isEqualTo("INT");
        assertThat(a.getRenderType()).isEqualTo("TEXT");
        assertThat(a.getNullRatio()).isEqualTo(0.0);
        assertThat(a.getDistinctCount()).isEqualTo(0L);
    }
}