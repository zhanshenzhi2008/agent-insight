package com.llm.insight.repository.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务明细日志表镜像（只读，映射到 llm-agent 的 log_llm_task_detail 表）。
 */
@Data
@Entity
@Table(name = "log_llm_task_detail", indexes = {
    @Index(name = "idx_log_llm_task_detail_request_id", columnList = "request_id"),
    @Index(name = "idx_task_unique_name", columnList = "task_unique_name"),
    @Index(name = "idx_agent_name", columnList = "agent_name")
})
public class LogLlmTaskDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "chat_message_id")
    private Long chatMessageId;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(name = "round_num")
    private Integer roundNum;

    @Column(name = "task_name", length = 128)
    private String taskName;

    @Column(name = "task_unique_name", length = 128)
    private String taskUniqueName;

    @Column(name = "comment", length = 255)
    private String comment;

    @Column(name = "task_type", length = 50)
    private String taskType;

    @Column(name = "is_result_plan")
    private Boolean resultPlan;

    @Column(name = "full_path", length = 1024)
    private String fullPath;

    @Column(name = "dynamic_plan_detail_id")
    private Long dynamicPlanDetailId;

    @Column(name = "task_index")
    private Integer taskIndex;

    @Column(name = "agent_try_count")
    private Integer agentTryCount;

    @Column(name = "task_try_count")
    private Integer taskTryCount;

    @Column(name = "is_final_result")
    private Boolean finalResult;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "result", columnDefinition = "MEDIUMTEXT")
    private String result;

    @Column(name = "result_type")
    private Integer resultType;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "task_end_time")
    private LocalDateTime taskEndTime;

    @Column(name = "log_llm_agent_main_id")
    private Long logLlmAgentMainId;
}
