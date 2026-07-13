package com.llm.insight.service.impl;

import com.llm.insight.dto.response.LlmCallDTO;
import com.llm.insight.dto.response.LlmCallDetailDTO;
import com.llm.insight.dto.response.TokenUsageDTO;
import com.llm.insight.repository.LogLlmHttpRequestRepository;
import com.llm.insight.repository.entity.LogLlmHttpRequest;
import com.llm.insight.service.LlmCallAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallAnalysisServiceImpl implements LlmCallAnalysisService {

    private final LogLlmHttpRequestRepository httpRequestRepository;

    @Override
    public List<LlmCallDTO> listLlmCalls(String requestId) {
        return httpRequestRepository.findByRequestId(requestId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<LlmCallDTO> getSlowCalls(String requestId, int topN) {
        return httpRequestRepository
                .findTopByRequestIdOrderBySpendTimeDesc(requestId,
                        PageRequest.of(0, topN))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public TokenUsageDTO getTokenUsage(String requestId) {
        int prompt = httpRequestRepository.sumPromptTokensByRequestId(requestId);
        int completion = httpRequestRepository.sumCompletionTokensByRequestId(requestId);
        int count = (int) httpRequestRepository.countByRequestId(requestId);

        return TokenUsageDTO.builder()
                .totalPromptTokens(prompt)
                .totalCompletionTokens(completion)
                .totalTokens(prompt + completion)
                .callCount(count)
                .build();
    }

    @Override
    public List<LlmCallDTO> getFailedCalls(String requestId) {
        return httpRequestRepository.findByRequestIdAndSuccessExpressionFalse(requestId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public LlmCallDetailDTO getCallDetail(Long callId) {
        return httpRequestRepository.findById(callId)
                .map(this::toDetailDTO)
                .orElse(null);
    }

    private LlmCallDTO toDTO(LogLlmHttpRequest req) {
        int totalTokens = (req.getPromptTokens() != null ? req.getPromptTokens() : 0)
                + (req.getCompletionTokens() != null ? req.getCompletionTokens() : 0);

        return LlmCallDTO.builder()
                .id(req.getId())
                .requestId(req.getRequestId())
                .agent(req.getAgent())
                .templateName(req.getTemplateName())
                .planUniqueName(req.getPlanUniqueName())
                .modelType(req.getModelType())
                .spendTime(req.getSpendTime())
                .promptTokens(req.getPromptTokens())
                .completionTokens(req.getCompletionTokens())
                .totalTokens(totalTokens)
                .successExpression(req.getSuccessExpression())
                .createTime(req.getCreateTime())
                .requestBodyPreview(truncate(req.getRequestBody(), 200))
                .responseBodyPreview(truncate(req.getResponseBody(), 200))
                .build();
    }

    private LlmCallDetailDTO toDetailDTO(LogLlmHttpRequest req) {
        return LlmCallDetailDTO.builder()
                .id(req.getId())
                .agent(req.getAgent())
                .templateName(req.getTemplateName())
                .modelType(req.getModelType())
                .spendTime(req.getSpendTime())
                .promptTokens(req.getPromptTokens())
                .completionTokens(req.getCompletionTokens())
                .successExpression(req.getSuccessExpression())
                .requestBody(req.getRequestBody())
                .requestUrl(req.getRequestUrl())
                .responseBody(req.getResponseBody())
                .createTime(req.getCreateTime())
                .build();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
