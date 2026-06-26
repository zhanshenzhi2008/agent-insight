package com.llm.insight.service.impl;

import com.llm.insight.common.PageResult;
import com.llm.insight.dto.request.RequestSearchQuery;
import com.llm.insight.dto.response.AgentInstanceDTO;
import com.llm.insight.dto.response.RequestOverviewDTO;
import com.llm.insight.dto.response.RequestSummaryDTO;
import com.llm.insight.repository.LogLlmAgentMainRepository;
import com.llm.insight.repository.LogLlmHttpRequestRepository;
import com.llm.insight.repository.LogLlmTaskDetailRepository;
import com.llm.insight.repository.entity.LogLlmAgentMain;
import com.llm.insight.service.RequestSearchService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestSearchServiceImpl implements RequestSearchService {

    private final LogLlmAgentMainRepository agentMainRepository;
    private final LogLlmTaskDetailRepository taskDetailRepository;
    private final LogLlmHttpRequestRepository httpRequestRepository;

    @Override
    public PageResult<RequestSummaryDTO> searchRequests(RequestSearchQuery query) {
        int page = query.getPage() != null ? query.getPage() : 0;
        int size = query.getSize() != null ? query.getSize() : 20;

        Specification<LogLlmAgentMain> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 只查入口 Agent
            predicates.add(cb.isTrue(root.get("entranceAgent")));

            if (StringUtils.hasText(query.getRequestId())) {
                predicates.add(cb.like(root.get("requestId"), "%" + query.getRequestId() + "%"));
            }
            if (StringUtils.hasText(query.getAgentName())) {
                predicates.add(cb.like(root.get("topAgentName"), "%" + query.getAgentName() + "%"));
            }
            if (query.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), query.getStartTime()));
            }
            if (query.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), query.getEndTime()));
            }
            if ("success".equalsIgnoreCase(query.getStatus())) {
                predicates.add(cb.isTrue(root.get("success")));
            } else if ("failed".equalsIgnoreCase(query.getStatus())) {
                predicates.add(cb.or(
                        cb.isFalse(root.get("success")),
                        cb.isNull(root.get("success"))
                ));
            }

            cq.where(predicates.toArray(new Predicate[0]));
            cq.orderBy(cb.desc(root.get("createTime")));
            return null;
        };

        Page<LogLlmAgentMain> result = agentMainRepository.findAll(spec, PageRequest.of(page, size));

        List<RequestSummaryDTO> summaries = result.getContent().stream()
                .map(this::toRequestSummary)
                .toList();

        return PageResult.of(summaries, result.getTotalElements(), page, size);
    }

    @Override
    public RequestOverviewDTO getOverview(String requestId) {
        List<LogLlmAgentMain> mains = agentMainRepository.findByRequestId(requestId);
        if (mains.isEmpty()) {
            return null;
        }

        LogLlmAgentMain entrance = mains.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEntranceAgent()))
                .max(Comparator.comparing(LogLlmAgentMain::getId))
                .orElse(mains.get(0));

        long totalTasks = taskDetailRepository.countByRequestId(requestId);
        long failedTasks = taskDetailRepository.countFailedByRequestId(requestId);
        long llmCalls = httpRequestRepository.countByRequestId(requestId);
        int promptTokens = httpRequestRepository.sumPromptTokensByRequestId(requestId);
        int completionTokens = httpRequestRepository.sumCompletionTokensByRequestId(requestId);

        Long duration = null;
        if (entrance.getAgentEndTime() != null && entrance.getCreateTime() != null) {
            duration = Duration.between(entrance.getCreateTime(), entrance.getAgentEndTime()).toMillis();
        }

        RequestOverviewDTO.RequestOverviewDTOBuilder builder = RequestOverviewDTO.builder()
                .requestId(requestId)
                .topAgentName(entrance.getTopAgentName())
                .success(entrance.getSuccess())
                .totalTaskCount((int) totalTasks)
                .failedTaskCount((int) failedTasks)
                .llmCallCount((int) llmCalls)
                .totalPromptTokens(promptTokens)
                .totalCompletionTokens(completionTokens)
                .totalDuration(duration)
                .createTime(entrance.getCreateTime())
                .agentInstances(listAgentInstances(requestId));

        return builder.build();
    }

    @Override
    public List<AgentInstanceDTO> listAgentInstances(String requestId) {
        List<LogLlmAgentMain> mains = agentMainRepository.findAllByRequestIdOrderByCreateTime(requestId);
        return mains.stream().map(main -> {
            List<LogLlmAgentMain> byAgentId = mains.stream()
                    .filter(m -> Objects.equals(m.getAgentId(), main.getAgentId()))
                    .toList();
            long taskCount = taskDetailRepository.findByLogLlmAgentMainId(main.getId()).size();
            Long duration = null;
            if (main.getAgentEndTime() != null && main.getCreateTime() != null) {
                duration = Duration.between(main.getCreateTime(), main.getAgentEndTime()).toMillis();
            }
            return AgentInstanceDTO.builder()
                    .agentId(main.getAgentId())
                    .agentName(main.getTopAgentName())
                    .entrance(Boolean.TRUE.equals(main.getEntranceAgent()))
                    .taskCount((int) taskCount)
                    .success(main.getSuccess())
                    .duration(duration)
                    .build();
        }).distinct().toList();
    }

    private RequestSummaryDTO toRequestSummary(LogLlmAgentMain main) {
        List<LogLlmAgentMain> allMains = agentMainRepository.findByRequestId(main.getRequestId());
        List<String> subAgents = allMains.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getEntranceAgent()))
                .map(LogLlmAgentMain::getTopAgentName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        long failedCount = taskDetailRepository.countFailedByRequestId(main.getRequestId());

        Long duration = null;
        if (main.getAgentEndTime() != null && main.getCreateTime() != null) {
            duration = Duration.between(main.getCreateTime(), main.getAgentEndTime()).toMillis();
        }

        return RequestSummaryDTO.builder()
                .requestId(main.getRequestId())
                .topAgentName(main.getTopAgentName())
                .agentId(main.getAgentId())
                .taskStatus(main.getTaskStatus())
                .success(main.getSuccess())
                .totalTaskCount((int) taskDetailRepository.countByRequestId(main.getRequestId()))
                .failedTaskCount((int) failedCount)
                .totalDuration(duration)
                .createTime(main.getCreateTime())
                .subAgentNames(subAgents)
                .build();
    }
}
