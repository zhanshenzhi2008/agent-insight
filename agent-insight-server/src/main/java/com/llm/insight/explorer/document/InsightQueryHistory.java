package com.llm.insight.explorer.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_query_history")
public class InsightQueryHistory {

    @Id
    private String id;

    @Indexed
    private String datasourceKey;

    private String tableName;

    /** 执行查询类型：DIRECT / SAVED_QUERY / FREE_SQL */
    private String queryType;

    /** 实际执行的查询语句 */
    private String executedQuery;

    /** 请求参数 JSON */
    private String requestParams;

    private Integer resultCount;

    /** 执行耗时 ms */
    private Long executionTimeMs;

    /** SUCCESS / FAILED / TIMEOUT */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    private String executedBy;

    @Indexed
    private LocalDateTime executedAt;

    /** 导出的文件大小（字节） */
    private Long exportSizeBytes;
}
