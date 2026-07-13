package com.llm.insight.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM HTTP 请求日志表镜像（只读，映射到 llm-agent 的 log_llm_http_request 表）。
 */
@Data
@Entity
@Table(name = "log_llm_http_request", indexes = {
    @Index(name = "idx_http_request_id", columnList = "request_id"),
    @Index(name = "idx_http_create_time", columnList = "create_time")
})
public class LogLlmHttpRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_id")
    private Long bizId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "task_detail_id")
    private Long taskDetailId;

    @Column(name = "agent", length = 50)
    private String agent;

    @Column(name = "agent_store_id")
    private Long agentStoreId;

    @Column(name = "template_name", length = 50)
    private String templateName;

    @Column(name = "plan_unique_name", length = 64)
    private String planUniqueName;

    @Column(name = "model_type", length = 32)
    private String modelType;

    @Column(name = "spend_time")
    private Long spendTime;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_param", columnDefinition = "json")
    private Map<String, String> requestParam;

    @Column(name = "request_body", columnDefinition = "MEDIUMTEXT")
    private String requestBody;

    @Column(name = "request_url", length = 1024)
    private String requestUrl;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "success_expression")
    private Boolean successExpression;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "create_by")
    private Long createById;
}
