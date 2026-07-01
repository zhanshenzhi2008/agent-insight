package com.llm.insight.controller;

import com.llm.insight.dto.response.*;
import com.llm.insight.service.SourceViewerService;
import com.llm.insight.service.TraceAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TraceController}.
 * Covers TC-F3 series (TC-F3-01 ~ TC-F3-08).
 *
 * Note: SourceViewerService is in {@link SourceController}, not TraceController.
 * The mapTaskToLine tests belong there.
 */
class TraceControllerTest {

    private TraceController controller(TraceAnalysisService svc) {
        return new TraceController(svc);
    }

    // ─── TC-F3-01: 获取执行轨迹（按 taskIndex 排序）────────────────────────────

    @Nested
    class GetExecutionTrace {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace returns trace sorted by taskIndex")
        void testGetTrace() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            TaskDetailDTO task1 = TaskDetailDTO.builder()
                    .id(1L).requestId("req-001").agentName("DataAgent")
                    .taskName("analyze").taskUniqueName("task_analyze")
                    .taskType("expression").taskIndex(0).success(true)
                    .result("{\"status\":\"ok\"}").build();

            TaskDetailDTO task2 = TaskDetailDTO.builder()
                    .id(2L).requestId("req-001").agentName("DataAgent")
                    .taskName("process").taskUniqueName("task_process")
                    .taskType("expression").taskIndex(1).success(false)
                    .errorMessage("NullPointerException").build();

            when(svc.getExecutionTrace(eq("req-001"), isNull()))
                    .thenReturn(List.of(task1, task2));

            var resp = controller(svc).getExecutionTrace("req-001", null);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getTaskIndex()).isEqualTo(0);
            assertThat(resp.getData().get(0).getSuccess()).isTrue();
            assertThat(resp.getData().get(1).getSuccess()).isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace?agentName=xxx filters by agent name")
        void testGetTraceByAgent() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            TaskDetailDTO task = TaskDetailDTO.builder()
                    .id(1L).requestId("req-001").agentName("SubAgent")
                    .taskIndex(0).success(true).build();

            when(svc.getExecutionTrace(eq("req-001"), eq("SubAgent")))
                    .thenReturn(List.of(task));

            var resp = controller(svc).getExecutionTrace("req-001", "SubAgent");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().get(0).getAgentName()).isEqualTo("SubAgent");
        }
    }

    // ─── TC-F3-03: 获取任务步骤明细 ────────────────────────────────────────────

    @Nested
    class GetTaskSteps {

        @Test
        @DisplayName("GET /api/v1/trace/{id}/steps returns step details")
        void testGetTaskSteps() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            TaskStepDTO step = TaskStepDTO.builder()
                    .id(1L).step(1).stepLabel("Template")
                    .template("prompt_template").input("input").output("output")
                    .success(true).duration(500L).build();

            when(svc.getTaskSteps(1L)).thenReturn(List.of(step));

            var resp = controller(svc).getTaskSteps(1L);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getStepLabel()).isEqualTo("Template");
            assertThat(resp.getData().get(0).getSuccess()).isTrue();
        }
    }

    // ─── TC-F3-04: 获取失败任务列表 ────────────────────────────────────────────

    @Nested
    class GetFailedTasks {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace/failed returns only failed tasks")
        void testGetFailedTasks() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            TaskDetailDTO failedTask = TaskDetailDTO.builder()
                    .id(2L).requestId("req-001").agentName("DataAgent")
                    .taskName("process").success(false)
                    .errorMessage("NullPointerException at line 42").build();

            when(svc.getFailedTasks("req-001")).thenReturn(List.of(failedTask));

            var resp = controller(svc).getFailedTasks("req-001");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getSuccess()).isFalse();
            assertThat(resp.getData().get(0).getErrorMessage()).isNotNull();
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace/failed returns empty when no failures")
        void testNoFailedTasks() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            when(svc.getFailedTasks("req-success")).thenReturn(List.of());

            var resp = controller(svc).getFailedTasks("req-success");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ─── TC-F3-05: 任务树结构构建 ───────────────────────────────────────────────

    @Nested
    class BuildTaskTree {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace/tree returns tree structure")
        void testGetTaskTree() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            TaskTreeNodeDTO rootNode = TaskTreeNodeDTO.builder()
                    .id(1L).name("root_task").success(true).build();

            TaskTreeDTO tree = TaskTreeDTO.builder()
                    .requestId("req-001")
                    .agentName("DataAgent")
                    .roots(List.of(rootNode))
                    .build();

            when(svc.buildTaskTree(eq("req-001"), isNull())).thenReturn(tree);

            var resp = controller(svc).buildTaskTree("req-001", null);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getRequestId()).isEqualTo("req-001");
            assertThat(resp.getData().getAgentName()).isEqualTo("DataAgent");
            assertThat(resp.getData().getRoots()).hasSize(1);
        }
    }

    // ─── TC-F3-06: 空轨迹数据 ──────────────────────────────────────────────────

    @Nested
    class EmptyTrace {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/trace returns empty list for no trace")
        void testEmptyTrace() {
            TraceAnalysisService svc = mock(TraceAnalysisService.class);
            when(svc.getExecutionTrace(eq("req-no-trace"), isNull())).thenReturn(List.of());

            var resp = controller(svc).getExecutionTrace("req-no-trace", null);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }
}
