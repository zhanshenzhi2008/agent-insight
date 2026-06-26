package com.llm.insight.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class RequestSearchQuery {

    @Parameter(description = "requestId（模糊搜索）")
    private String requestId;

    @Parameter(description = "Agent 名称过滤")
    private String agentName;

    @Parameter(description = "开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Parameter(description = "结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Parameter(description = "状态过滤：success / failed / all")
    private String status;

    @Parameter(description = "页码（0-based）")
    private Integer page = 0;

    @Parameter(description = "每页大小")
    private Integer size = 20;
}
