package com.llm.insight.repository.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务步骤日志表镜像（只读，映射到 llm-agent 的 log_llm_task_step 表）。
 */
@Data
@Entity
@Table(name = "log_llm_task_step", indexes = {
    @Index(name = "idx_task_step_detail_id", columnList = "log_llm_task_detail_id"),
    @Index(name = "idx_step_request_id", columnList = "request_id")
})
public class LogLlmTaskStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "step")
    private Integer step;

    @Column(name = "template", length = 1024)
    private String template;

    @Column(name = "result_type")
    private Integer resultType;

    @Column(name = "input", columnDefinition = "MEDIUMTEXT")
    private String input;

    @Column(name = "output", columnDefinition = "MEDIUMTEXT")
    private String output;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "log_llm_task_detail_id")
    private Long logLlmTaskDetailId;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
