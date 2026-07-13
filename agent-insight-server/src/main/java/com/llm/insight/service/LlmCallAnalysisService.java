package com.llm.insight.service;

import com.llm.insight.dto.response.*;

import java.util.List;

public interface LlmCallAnalysisService {

    List<LlmCallDTO> listLlmCalls(String requestId);

    List<LlmCallDTO> getSlowCalls(String requestId, int topN);

    TokenUsageDTO getTokenUsage(String requestId);

    List<LlmCallDTO> getFailedCalls(String requestId);

    LlmCallDetailDTO getCallDetail(Long callId);
}
