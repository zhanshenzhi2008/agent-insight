package com.llm.insight.repository.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 运行日志主表镜像（只读，映射到 llm-agent 的 log_llm_agent_main 表）。
 * 实体字段与 {@link com.llm.agent.runtime.log.entity.LogLlmAgentMain} 保持一致。
 */
@Data
@Entity
@Table(name = "log_llm_agent_main", indexes = {
    @Index(name = "idx_request_id", columnList = "request_id"),
    @Index(name = "idx_agent_id", columnList = "agent_id"),
    @Index(name = "idx_create_time", columnList = "create_time")
})
public class LogLlmAgentMain implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_id")
    private String bizId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "top_agent_name", length = 64)
    private String topAgentName;

    @Column(name = "is_entrance_agent")
    private Boolean entranceAgent;

    @Column(name = "title", length = 64)
    private String title;

    @Column(name = "task_status")
    private Integer taskStatus;

    @Column(name = "agent_status")
    private Integer agentStatus;

    @Column(name = "shift_must_task")
    private Integer shiftMustTask;

    @Column(name = "agent_try_count")
    private Integer agentTryCount;

    @Column(name = "task_index")
    private Integer taskIndex;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "task_try_count")
    private Integer taskTryCount;

    @Column(name = "final_task_detail_id")
    private Long finalTaskDetailId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "create_by")
    private Long createById;

    @Column(name = "agent_result", columnDefinition = "MEDIUMTEXT")
    private String agentResult;

    @Column(name = "agent_end_time")
    private LocalDateTime agentEndTime;
}
