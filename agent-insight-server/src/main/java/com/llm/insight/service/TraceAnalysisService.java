package com.llm.insight.service;

import com.llm.insight.dto.response.*;

import java.util.List;

public interface TraceAnalysisService {

    List<TaskDetailDTO> getExecutionTrace(String requestId, String agentName);

    TaskTreeDTO buildTaskTree(String requestId, String agentName);

    List<TaskStepDTO> getTaskSteps(Long taskDetailId);

    List<TaskDetailDTO> getFailedTasks(String requestId);
}
