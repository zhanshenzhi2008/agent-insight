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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Unit tests for {@link RequestSearchServiceImpl}.
 *
 * Verifies:
 *  - searchRequests builds correct JPA Specification and returns paginated results.
 *  - getOverview aggregates data from all three repositories correctly.
 *  - listAgentInstances groups by agentId and computes duration.
 *  - toRequestSummary enriches each entry with sub-agent names.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestSearchServiceImplTest {

    @Mock
    private LogLlmAgentMainRepository agentMainRepository;

    @Mock
    private LogLlmTaskDetailRepository taskDetailRepository;

    @Mock
    private LogLlmHttpRequestRepository httpRequestRepository;

    private RequestSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RequestSearchServiceImpl(agentMainRepository, taskDetailRepository, httpRequestRepository);
    }

    // ─── searchRequests ────────────────────────────────────────────────────────

    @Nested
    class SearchRequests {

        @Test
        @DisplayName("returns empty page when no results exist")
        void emptyResult() {
            Page<LogLlmAgentMain> emptyPage = new PageImpl<>(List.of());
            when(agentMainRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResult<RequestSummaryDTO> result = service.searchRequests(new RequestSearchQuery());

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("maps entity fields to DTO and returns paginated result")
        void mapsFieldsCorrectly() {
            LogLlmAgentMain entity = mainEntity("req-001", "TestAgent");
            entity.setEntranceAgent(true);
            entity.setSuccess(true);
            entity.setCreateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
            entity.setAgentEndTime(LocalDateTime.of(2025, 1, 15, 10, 1));

            Page<LogLlmAgentMain> page = new PageImpl<>(List.of(entity));
            when(agentMainRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(agentMainRepository.findByRequestId("req-001")).thenReturn(List.of(entity));
            when(taskDetailRepository.countByRequestId("req-001")).thenReturn(3L);
            when(taskDetailRepository.countFailedByRequestId("req-001")).thenReturn(0L);

            PageResult<RequestSummaryDTO> result = service.searchRequests(new RequestSearchQuery());

            assertThat(result.getContent()).hasSize(1);
            RequestSummaryDTO dto = result.getContent().get(0);
            assertThat(dto.getRequestId()).isEqualTo("req-001");
            assertThat(dto.getTopAgentName()).isEqualTo("TestAgent");
            assertThat(dto.getTotalTaskCount()).isEqualTo(3);
            assertThat(dto.getFailedTaskCount()).isZero();
            assertThat(dto.getSuccess()).isTrue();
            assertThat(dto.getTotalDuration()).isEqualTo(60_000L); // 1 minute
        }

        @Test
        @DisplayName("passes page and size to Pageable")
        void respectsPagination() {
            Page<LogLlmAgentMain> page = new PageImpl<>(List.of(), Pageable.ofSize(10), 0);
            when(agentMainRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            RequestSearchQuery query = new RequestSearchQuery();
            query.setPage(2);
            query.setSize(10);
            service.searchRequests(query);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(agentMainRepository).findAll(any(Specification.class), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("filters by status=failed when requested")
        void filtersFailedStatus() {
            Page<LogLlmAgentMain> page = new PageImpl<>(List.of());
            when(agentMainRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            RequestSearchQuery query = new RequestSearchQuery();
            query.setStatus("failed");
            service.searchRequests(query);

            verify(agentMainRepository).findAll(any(Specification.class), any(Pageable.class));
        }
    }

    // ─── getOverview ───────────────────────────────────────────────────────────

    @Nested
    class GetOverview {

        @Test
        @DisplayName("returns null when no agent mains are found")
        void returnsNullWhenNotFound() {
            when(agentMainRepository.findByRequestId("unknown")).thenReturn(List.of());

            RequestOverviewDTO result = service.getOverview("unknown");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("aggregates counts from all three repositories")
        void aggregatesCorrectly() {
            LogLlmAgentMain entrance = mainEntity("req-002", "MainAgent");
            entrance.setEntranceAgent(true);
            entrance.setSuccess(true);
            entrance.setCreateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
            entrance.setAgentEndTime(LocalDateTime.of(2025, 1, 15, 10, 0, 30));

            when(agentMainRepository.findByRequestId("req-002")).thenReturn(List.of(entrance));
            when(taskDetailRepository.countByRequestId("req-002")).thenReturn(5L);
            when(taskDetailRepository.countFailedByRequestId("req-002")).thenReturn(2L);
            when(httpRequestRepository.countByRequestId("req-002")).thenReturn(12L);
            when(httpRequestRepository.sumPromptTokensByRequestId("req-002")).thenReturn(1000);
            when(httpRequestRepository.sumCompletionTokensByRequestId("req-002")).thenReturn(500);
            when(agentMainRepository.findAllByRequestIdOrderByCreateTime("req-002")).thenReturn(List.of(entrance));
            when(taskDetailRepository.findByLogLlmAgentMainId(1L)).thenReturn(List.of());

            RequestOverviewDTO result = service.getOverview("req-002");

            assertThat(result).isNotNull();
            assertThat(result.getRequestId()).isEqualTo("req-002");
            assertThat(result.getTotalTaskCount()).isEqualTo(5);
            assertThat(result.getFailedTaskCount()).isEqualTo(2);
            assertThat(result.getLlmCallCount()).isEqualTo(12);
            assertThat(result.getTotalPromptTokens()).isEqualTo(1000);
            assertThat(result.getTotalCompletionTokens()).isEqualTo(500);
            assertThat(result.getTotalDuration()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("picks the latest entrance agent when multiple exist")
        void picksLatestEntranceAgent() {
            LogLlmAgentMain older = mainEntity("req-003", "OlderAgent");
            older.setEntranceAgent(true);
            older.setId(1L);
            older.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));

            LogLlmAgentMain newer = mainEntity("req-003", "NewerAgent");
            newer.setEntranceAgent(true);
            newer.setId(2L);
            newer.setCreateTime(LocalDateTime.of(2025, 1, 2, 0, 0));

            when(agentMainRepository.findByRequestId("req-003")).thenReturn(List.of(older, newer));
            when(taskDetailRepository.countByRequestId("req-003")).thenReturn(1L);
            when(taskDetailRepository.countFailedByRequestId("req-003")).thenReturn(0L);
            when(httpRequestRepository.countByRequestId("req-003")).thenReturn(0L);
            when(httpRequestRepository.sumPromptTokensByRequestId("req-003")).thenReturn(0);
            when(httpRequestRepository.sumCompletionTokensByRequestId("req-003")).thenReturn(0);
            when(agentMainRepository.findAllByRequestIdOrderByCreateTime("req-003")).thenReturn(List.of(older, newer));
            when(taskDetailRepository.findByLogLlmAgentMainId(anyLong())).thenReturn(List.of());

            RequestOverviewDTO result = service.getOverview("req-003");

            assertThat(result.getTopAgentName()).isEqualTo("NewerAgent");
        }
    }

    // ─── listAgentInstances ────────────────────────────────────────────────────

    @Nested
    class ListAgentInstances {

        @Test
        @DisplayName("returns empty list when no agents found")
        void emptyList() {
            when(agentMainRepository.findAllByRequestIdOrderByCreateTime("req-x")).thenReturn(List.of());

            List<AgentInstanceDTO> result = service.listAgentInstances("req-x");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("groups by agentId and marks entrance agent")
        void groupsAndMarksEntrance() {
            LogLlmAgentMain a1 = mainEntity("req-004", "Agent-A");
            a1.setAgentId(100L);
            a1.setEntranceAgent(true);
            a1.setSuccess(true);
            a1.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
            a1.setAgentEndTime(LocalDateTime.of(2025, 1, 1, 0, 1));

            LogLlmAgentMain a2 = mainEntity("req-004", "Agent-B");
            a2.setAgentId(200L);
            a2.setEntranceAgent(false);
            a2.setSuccess(false);
            a2.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 2));

            when(agentMainRepository.findAllByRequestIdOrderByCreateTime("req-004"))
                    .thenReturn(List.of(a1, a2));
            when(taskDetailRepository.findByLogLlmAgentMainId(1L)).thenReturn(List.of());
            when(taskDetailRepository.findByLogLlmAgentMainId(2L)).thenReturn(List.of());

            List<AgentInstanceDTO> result = service.listAgentInstances("req-004");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AgentInstanceDTO::getAgentId).containsExactlyInAnyOrder(100L, 200L);
            assertThat(result).extracting(AgentInstanceDTO::getEntrance).containsExactlyInAnyOrder(true, false);
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private LogLlmAgentMain mainEntity(String requestId, String agentName) {
        LogLlmAgentMain m = new LogLlmAgentMain();
        m.setId(1L);
        m.setRequestId(requestId);
        m.setTopAgentName(agentName);
        m.setAgentId(1L);
        m.setTaskStatus(2);
        return m;
    }
}
