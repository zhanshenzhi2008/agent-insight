package com.llm.insight.service;

import com.llm.insight.common.PageResult;
import com.llm.insight.dto.request.RequestSearchQuery;
import com.llm.insight.dto.response.AgentInstanceDTO;
import com.llm.insight.dto.response.RequestOverviewDTO;
import com.llm.insight.dto.response.RequestSummaryDTO;

import java.util.List;

public interface RequestSearchService {

    PageResult<RequestSummaryDTO> searchRequests(RequestSearchQuery query);

    RequestOverviewDTO getOverview(String requestId);

    List<AgentInstanceDTO> listAgentInstances(String requestId);
}
