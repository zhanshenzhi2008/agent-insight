package com.llm.insight.controller;

import com.llm.insight.dto.response.TaskDetailDTO;
import com.llm.insight.dto.response.TaskStepDTO;
import com.llm.insight.dto.response.TaskTreeDTO;
import com.llm.insight.dto.response.TaskTreeNodeDTO;
import com.llm.insight.service.TraceAnalysisService;
import com.llm.insight.support.BaseWebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TraceController}.
 *
 * <p>Spring Boot 4.x removed {@code @WebMvcTest}; we wire {@link org.springframework.test.web.servlet.MockMvc}
 * via {@code MockMvcBuilders.standaloneSetup}, which still exercises the real
 * Spring MVC serialization layer (Jackson, {@code @PathVariable}, {@code @RequestParam},
 * {@code @RestControllerAdvice}, UTF-8 JSON) — covering the gaps left by
 * the pure-Mockito {@code TraceControllerTest}.
 */
@DisplayName("TraceController integration (MockMvc standaloneSetup)")
class TraceControllerIntegrationTest extends BaseWebMvcTest {

    private final TraceAnalysisService traceAnalysisService = mock(TraceAnalysisService.class);

    @Override
    protected Object[] controllerOrAdvice() {
        return new Object[]{new TraceController(traceAnalysisService)};
    }

    @Override
    protected Object[] getControllerAdvices() {
        return new Object[]{new GlobalExceptionHandler()};
    }

    // ─── TC-F3-INT-01: getExecutionTrace ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/trace")
    class GetExecutionTrace {

        @Test
        @DisplayName("returns 200 with envelope { code:0, data:[…] }")
        void returns200WithEnvelope() throws Exception {
            TaskDetailDTO task = TaskDetailDTO.builder()
                    .id(1L).requestId("req-001").agentName("DataAgent")
                    .taskName("analyze").taskUniqueName("task_analyze")
                    .taskType("expression").taskIndex(0).success(true)
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0, 0))
                    .build();
            when(traceAnalysisService.getExecutionTrace(eq("req-001"), isNull()))
                    .thenReturn(List.of(task));

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].requestId").value("req-001"))
                    .andExpect(jsonPath("$.data[0].agentName").value("DataAgent"))
                    .andExpect(jsonPath("$.data[0].taskIndex").value(0))
                    .andExpect(jsonPath("$.data[0].success").value(true))
                    .andExpect(jsonPath("$.data[0].createTime").exists());

            verify(traceAnalysisService).getExecutionTrace(eq("req-001"), isNull());
        }

        @Test
        @DisplayName("passes optional agentName query parameter through")
        void forwardsAgentName() throws Exception {
            when(traceAnalysisService.getExecutionTrace(eq("req-002"), eq("SubAgent")))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace", "req-002")
                            .param("agentName", "SubAgent")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(traceAnalysisService).getExecutionTrace(eq("req-002"), eq("SubAgent"));
        }

        @Test
        @DisplayName("returns empty data array when service returns empty")
        void returnsEmptyData() throws Exception {
            when(traceAnalysisService.getExecutionTrace(eq("req-empty"), isNull()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace", "req-empty")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── TC-F3-INT-02: buildTaskTree ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/trace/tree")
    class BuildTaskTree {

        @Test
        @DisplayName("returns tree with nested children")
        void returnsTreeStructure() throws Exception {
            TaskTreeNodeDTO leaf = TaskTreeNodeDTO.builder()
                    .id(2L).name("child").type("expression").success(true).duration(120L)
                    .children(List.of())
                    .build();
            TaskTreeNodeDTO root = TaskTreeNodeDTO.builder()
                    .id(1L).name("root").type("expression").success(true).duration(500L)
                    .children(List.of(leaf))
                    .build();
            TaskTreeDTO tree = TaskTreeDTO.builder()
                    .requestId("req-001").agentName("DataAgent")
                    .roots(List.of(root))
                    .build();

            when(traceAnalysisService.buildTaskTree(eq("req-001"), isNull()))
                    .thenReturn(tree);

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace/tree", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.requestId").value("req-001"))
                    .andExpect(jsonPath("$.data.agentName").value("DataAgent"))
                    .andExpect(jsonPath("$.data.roots").isArray())
                    .andExpect(jsonPath("$.data.roots[0].name").value("root"))
                    .andExpect(jsonPath("$.data.roots[0].children[0].name").value("child"))
                    .andExpect(jsonPath("$.data.roots[0].children[0].duration").value(120));
        }
    }

    // ─── TC-F3-INT-03: getTaskSteps ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/trace/{taskDetailId}/steps")
    class GetTaskSteps {

        @Test
        @DisplayName("binds Long path variable and serializes step DTOs")
        void returnsStepsByLongId() throws Exception {
            TaskStepDTO step = TaskStepDTO.builder()
                    .id(10L).step(1).stepLabel("Template")
                    .template("prompt_v1")
                    .input("{\"q\":\"hi\"}").output("{\"a\":\"hello\"}")
                    .resultType(1).success(true).duration(500L)
                    .endTime(LocalDateTime.of(2026, 7, 1, 10, 0, 5))
                    .build();
            when(traceAnalysisService.getTaskSteps(42L)).thenReturn(List.of(step));

            mockMvc.perform(get("/api/v1/trace/{taskDetailId}/steps", 42L)
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].step").value(1))
                    .andExpect(jsonPath("$.data[0].stepLabel").value("Template"))
                    .andExpect(jsonPath("$.data[0].success").value(true))
                    .andExpect(jsonPath("$.data[0].duration").value(500))
                    .andExpect(jsonPath("$.data[0].endTime").exists());
        }

        @Test
        @DisplayName("returns 400 (not 500) when path variable is not numeric")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(get("/api/v1/trace/{taskDetailId}/steps", "not-a-number")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest());

            verify(traceAnalysisService, never()).getTaskSteps(any());
        }
    }

    // ─── TC-F3-INT-04: getFailedTasks ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/trace/failed")
    class GetFailedTasks {

        @Test
        @DisplayName("returns only failed tasks including errorMessage")
        void returnsFailedTasks() throws Exception {
            TaskDetailDTO failed = TaskDetailDTO.builder()
                    .id(7L).requestId("req-err").agentName("DataAgent")
                    .taskName("process").taskIndex(2).success(false)
                    .errorMessage("NullPointerException at line 42")
                    .build();
            when(traceAnalysisService.getFailedTasks("req-err")).thenReturn(List.of(failed));

            String json = mockMvc.perform(get("/api/v1/requests/{requestId}/trace/failed", "req-err")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].success").value(false))
                    .andExpect(jsonPath("$.data[0].errorMessage").value("NullPointerException at line 42"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(json).contains("\"code\":0").contains("\"message\":\"success\"");
        }

        @Test
        @DisplayName("returns empty array when no failures")
        void returnsEmptyFailedList() throws Exception {
            when(traceAnalysisService.getFailedTasks("req-ok")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace/failed", "req-ok")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── TC-F3-INT-05: error envelope (GlobalExceptionHandler) ───────────────

    @Nested
    @DisplayName("error handling")
    class ErrorEnvelope {

        @Test
        @DisplayName("IllegalArgumentException → 400 with {code:400, message}")
        void mapsIllegalArgumentTo400() throws Exception {
            when(traceAnalysisService.getFailedTasks("bad"))
                    .thenThrow(new IllegalArgumentException("requestId 不能为空"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace/failed", "bad")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("requestId 不能为空"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("unhandled Exception → 500 via GlobalExceptionHandler")
        void mapsUnexpectedExceptionTo500() throws Exception {
            when(traceAnalysisService.getExecutionTrace(eq("boom"), isNull()))
                    .thenThrow(new RuntimeException("boom-original-cause"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/trace", "boom")
                            .accept("application/json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("boom-original-cause")));
        }
    }
}
