package com.llm.insight.service.impl;

import com.llm.insight.common.InsightConstants;
import com.llm.insight.dto.response.*;
import com.llm.insight.repository.LogLlmTaskDetailRepository;
import com.llm.insight.repository.LogLlmTaskStepRepository;
import com.llm.insight.repository.entity.LogLlmTaskDetail;
import com.llm.insight.repository.entity.LogLlmTaskStep;
import com.llm.insight.service.SourceViewerService;
import com.llm.insight.service.TraceAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceAnalysisServiceImpl implements TraceAnalysisService {

    private final LogLlmTaskDetailRepository taskDetailRepository;
    private final LogLlmTaskStepRepository taskStepRepository;
    private final SourceViewerService sourceViewerService;

    @Override
    public List<TaskDetailDTO> getExecutionTrace(String requestId, String agentName) {
        List<LogLlmTaskDetail> details;
        if (StringUtils.hasText(agentName)) {
            details = taskDetailRepository.findByRequestIdAndAgentNameOrderByTaskIndex(requestId, agentName);
        } else {
            details = taskDetailRepository.findByRequestIdOrderByTaskIndex(requestId);
        }

        if (details.isEmpty()) {
            return List.of();
        }

        List<Long> detailIds = details.stream().map(LogLlmTaskDetail::getId).toList();
        Map<Long, List<LogLlmTaskStep>> stepsMap = taskStepRepository.findByDetailIds(detailIds).stream()
                .collect(Collectors.groupingBy(LogLlmTaskStep::getLogLlmTaskDetailId));

        return details.stream().map(detail -> {
            TaskDetailDTO dto = toTaskDetailDTO(detail);
            dto.setSteps(toStepDTOs(stepsMap.getOrDefault(detail.getId(), List.of())));

            SourceLineMappingDTO lineMapping =
                    sourceViewerService.mapTaskToLine(detail.getAgentName(), detail.getTaskUniqueName());
            if (lineMapping != null) {
                dto.setSourceFile(lineMapping.getFilePath());
                dto.setSourceStartLine(lineMapping.getStartLine());
                dto.setSourceEndLine(lineMapping.getEndLine());
            }
            return dto;
        }).toList();
    }

    @Override
    public TaskTreeDTO buildTaskTree(String requestId, String agentName) {
        List<TaskDetailDTO> trace = getExecutionTrace(requestId, agentName);

        List<TaskTreeNodeDTO> roots = new ArrayList<>();
        Map<String, TaskTreeNodeDTO> nodeMap = new LinkedHashMap<>();

        for (TaskDetailDTO task : trace) {
            String path = task.getFullPath();
            if (path == null || path.isEmpty()) {
                path = task.getTaskUniqueName();
            }

            TaskTreeNodeDTO node = TaskTreeNodeDTO.builder()
                    .id(task.getId())
                    .name(task.getTaskUniqueName())
                    .type(task.getTaskType())
                    .success(task.getSuccess())
                    .duration(task.getDuration())
                    .children(new ArrayList<>())
                    .build();

            if (path.contains("/")) {
                String[] parts = path.split("/");
                TaskTreeNodeDTO currentParent = null;

                for (int i = 0; i < parts.length; i++) {
                    String currentPath = parts[i];
                    String fullCurrentPath = i == 0 ? currentPath : parts[i - 1] + "/" + currentPath;

                    if (!nodeMap.containsKey(fullCurrentPath)) {
                        TaskTreeNodeDTO newNode = TaskTreeNodeDTO.builder()
                                .name(currentPath)
                                .children(new ArrayList<>())
                                .build();
                        nodeMap.put(fullCurrentPath, newNode);

                        if (currentParent == null) {
                            roots.add(newNode);
                        } else {
                            currentParent.getChildren().add(newNode);
                        }
                    }
                    currentParent = nodeMap.get(fullCurrentPath);
                }

                // Attach the actual task node as a child of the deepest intermediate node.
                if (currentParent != null) {
                    currentParent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            } else {
                nodeMap.put(path, node);
                roots.add(node);
            }
        }

        return TaskTreeDTO.builder()
                .requestId(requestId)
                .agentName(agentName)
                .roots(roots)
                .build();
    }

    @Override
    public List<TaskStepDTO> getTaskSteps(Long taskDetailId) {
        return taskStepRepository.findByLogLlmTaskDetailIdOrderById(taskDetailId).stream()
                .map(this::toStepDTO)
                .toList();
    }

    @Override
    public List<TaskDetailDTO> getFailedTasks(String requestId) {
        return taskDetailRepository.findFailedByRequestId(requestId).stream()
                .map(this::toTaskDetailDTO)
                .toList();
    }

    private TaskDetailDTO toTaskDetailDTO(LogLlmTaskDetail detail) {
        Long duration = null;
        if (detail.getTaskEndTime() != null && detail.getCreateTime() != null) {
            duration = Duration.between(detail.getCreateTime(), detail.getTaskEndTime()).toMillis();
        }

        String result = detail.getResult();
        if (result != null && result.length() > 500) {
            result = result.substring(0, 500) + "...（已截断）";
        }

        return TaskDetailDTO.builder()
                .id(detail.getId())
                .requestId(detail.getRequestId())
                .agentName(detail.getAgentName())
                .taskName(detail.getTaskName())
                .taskUniqueName(detail.getTaskUniqueName())
                .taskType(detail.getTaskType())
                .taskIndex(detail.getTaskIndex())
                .fullPath(detail.getFullPath())
                .success(detail.getSuccess())
                .result(result)
                .resultType(detail.getResultType())
                .errorMessage(detail.getComment())
                .duration(duration)
                .agentTryCount(detail.getAgentTryCount())
                .taskTryCount(detail.getTaskTryCount())
                .finalResult(detail.getFinalResult())
                .createTime(detail.getCreateTime())
                .taskEndTime(detail.getTaskEndTime())
                .build();
    }

    private TaskStepDTO toStepDTO(LogLlmTaskStep step) {
        String stepLabel = InsightConstants.stepToLabel(
                String.valueOf(step.getStep()));
        Long duration = null;
        if (step.getEndTime() != null && step.getInput() != null) {
            // 近似估算
        }

        return TaskStepDTO.builder()
                .id(step.getId())
                .step(step.getStep())
                .stepLabel(stepLabel)
                .template(step.getTemplate())
                .input(step.getInput())
                .output(step.getOutput())
                .resultType(step.getResultType())
                .success(step.getSuccess())
                .endTime(step.getEndTime())
                .build();
    }

    private List<TaskStepDTO> toStepDTOs(List<LogLlmTaskStep> steps) {
        return steps.stream().map(this::toStepDTO).toList();
    }
}
