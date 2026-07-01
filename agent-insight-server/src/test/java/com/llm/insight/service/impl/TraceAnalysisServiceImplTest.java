package com.llm.insight.service.impl;

import com.llm.insight.dto.response.SourceLineMappingDTO;
import com.llm.insight.dto.response.TaskDetailDTO;
import com.llm.insight.dto.response.TaskStepDTO;
import com.llm.insight.dto.response.TaskTreeDTO;
import com.llm.insight.dto.response.TaskTreeNodeDTO;
import com.llm.insight.repository.LogLlmTaskDetailRepository;
import com.llm.insight.repository.LogLlmTaskStepRepository;
import com.llm.insight.repository.entity.LogLlmTaskDetail;
import com.llm.insight.repository.entity.LogLlmTaskStep;
import com.llm.insight.service.SourceViewerService;
import com.llm.insight.service.TraceAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TraceAnalysisServiceImpl}.
 *
 * Verifies:
 *  - getExecutionTrace builds correct task list with steps.
 *  - buildTaskTree organizes flat tasks into a hierarchical structure.
 *  - getTaskSteps delegates to repository.
 *  - getFailedTasks returns failed task DTOs.
 *  - Long results are truncated at 500 chars.
 */
@ExtendWith(MockitoExtension.class)
class TraceAnalysisServiceImplTest {

    @Mock
    private LogLlmTaskDetailRepository taskDetailRepository;

    @Mock
    private LogLlmTaskStepRepository taskStepRepository;

    @Mock
    private SourceViewerService sourceViewerService;

    private TraceAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TraceAnalysisServiceImpl(taskDetailRepository, taskStepRepository, sourceViewerService);
    }

    // ─── getExecutionTrace ─────────────────────────────────────────────────────

    @Nested
    class GetExecutionTrace {

        @Test
        @DisplayName("returns empty list when no tasks found")
        void emptyResult() {
            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-x"))
                    .thenReturn(List.of());

            List<TaskDetailDTO> result = service.getExecutionTrace("req-x", null);

            assertThat(result).isEmpty();
            verifyNoInteractions(taskStepRepository);
        }

        @Test
        @DisplayName("filters by agentName when provided")
        void filtersByAgentName() {
            when(taskDetailRepository.findByRequestIdAndAgentNameOrderByTaskIndex("req-001", "AgentA"))
                    .thenReturn(List.of(taskDetail(1L, "req-001", "AgentA", "task-1")));
            when(taskStepRepository.findByDetailIds(List.of(1L))).thenReturn(List.of());

            List<TaskDetailDTO> result = service.getExecutionTrace("req-001", "AgentA");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAgentName()).isEqualTo("AgentA");
            verify(taskDetailRepository)
                    .findByRequestIdAndAgentNameOrderByTaskIndex("req-001", "AgentA");
        }

        @Test
        @DisplayName("maps steps from repository and attaches to task DTO")
        void mapsStepsCorrectly() {
            LogLlmTaskDetail detail = taskDetail(5L, "req-002", "AgentB", "task-x");
            LogLlmTaskStep step = taskStep(100L, 5L);

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-002"))
                    .thenReturn(List.of(detail));
            when(taskStepRepository.findByDetailIds(List.of(5L)))
                    .thenReturn(List.of(step));
            when(sourceViewerService.mapTaskToLine(any(), any())).thenReturn(null);

            List<TaskDetailDTO> result = service.getExecutionTrace("req-002", null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSteps()).hasSize(1);
            assertThat(result.get(0).getSteps().get(0).getId()).isEqualTo(100L);
            assertThat(result.get(0).getSteps().get(0).getStep()).isEqualTo(1);
        }

        @Test
        @DisplayName("truncates result field at 500 characters")
        void truncatesLongResult() {
            LogLlmTaskDetail detail = taskDetail(6L, "req-003", "AgentC", "task-y");
            detail.setResult("A".repeat(600));
            detail.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
            detail.setTaskEndTime(LocalDateTime.of(2025, 1, 1, 0, 0, 5));

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-003"))
                    .thenReturn(List.of(detail));
            when(taskStepRepository.findByDetailIds(List.of(6L))).thenReturn(List.of());
            when(sourceViewerService.mapTaskToLine(any(), any())).thenReturn(null);

            List<TaskDetailDTO> result = service.getExecutionTrace("req-003", null);

            assertThat(result.get(0).getResult()).hasSize(508); // 500 + "..." (3) + "（已截断）" (5)
            assertThat(result.get(0).getResult()).endsWith("...（已截断）");
        }

        @Test
        @DisplayName("attaches source line mapping when available")
        void attachesSourceMapping() {
            LogLlmTaskDetail detail = taskDetail(7L, "req-004", "AgentD", "task-z");

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-004"))
                    .thenReturn(List.of(detail));
            when(taskStepRepository.findByDetailIds(List.of(7L))).thenReturn(List.of());
            when(sourceViewerService.mapTaskToLine("AgentD", "task-z"))
                    .thenReturn(SourceLineMappingDTO.builder()
                            .filePath("/src/Agent.java")
                            .startLine(12)
                            .endLine(45)
                            .build());

            List<TaskDetailDTO> result = service.getExecutionTrace("req-004", null);

            assertThat(result.get(0).getSourceFile()).isEqualTo("/src/Agent.java");
            assertThat(result.get(0).getSourceStartLine()).isEqualTo(12);
            assertThat(result.get(0).getSourceEndLine()).isEqualTo(45);
        }
    }

    // ─── buildTaskTree ────────────────────────────────────────────────────────

    @Nested
    class BuildTaskTree {

        @Test
        @DisplayName("returns tree with flat nodes when no slashes in path")
        void flatNodes() {
            LogLlmTaskDetail d1 = taskDetail(10L, "req-010", "A", "root-task");
            d1.setFullPath("root-task");
            d1.setTaskType("action");

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-010"))
                    .thenReturn(List.of(d1));
            when(taskStepRepository.findByDetailIds(List.of(10L))).thenReturn(List.of());
            when(sourceViewerService.mapTaskToLine(any(), any())).thenReturn(null);

            TaskTreeDTO tree = service.buildTaskTree("req-010", null);

            assertThat(tree.getRoots()).hasSize(1);
            assertThat(tree.getRoots().get(0).getName()).isEqualTo("root-task");
            assertThat(tree.getRoots().get(0).getType()).isEqualTo("action");
        }

        @Test
        @DisplayName("builds hierarchical tree with intermediate nodes from path segments")
        void nestedHierarchy() {
            LogLlmTaskDetail parent = taskDetail(20L, "req-020", "A", "root/branch/leaf");
            parent.setFullPath("root/branch/leaf");
            parent.setTaskType("plan");

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-020"))
                    .thenReturn(List.of(parent));
            when(taskStepRepository.findByDetailIds(List.of(20L))).thenReturn(List.of());
            when(sourceViewerService.mapTaskToLine(any(), any())).thenReturn(null);

            TaskTreeDTO tree = service.buildTaskTree("req-020", null);

            // Three-level hierarchy: root → branch → leaf → task-node
            assertThat(tree.getRoots()).hasSize(1);
            TaskTreeNodeDTO rootNode = tree.getRoots().get(0);
            assertThat(rootNode.getName()).isEqualTo("root");
            assertThat(rootNode.getId()).isNull(); // intermediate nodes have no id

            assertThat(rootNode.getChildren()).hasSize(1);
            TaskTreeNodeDTO branchNode = rootNode.getChildren().get(0);
            assertThat(branchNode.getName()).isEqualTo("branch");
            assertThat(branchNode.getId()).isNull();

            assertThat(branchNode.getChildren()).hasSize(1);
            TaskTreeNodeDTO leafNode = branchNode.getChildren().get(0);
            assertThat(leafNode.getName()).isEqualTo("leaf");
            assertThat(leafNode.getId()).isNull();

            // The actual task node is attached as a child of the leaf.
            assertThat(leafNode.getChildren()).hasSize(1);
            TaskTreeNodeDTO taskNode = leafNode.getChildren().get(0);
            assertThat(taskNode.getName()).isEqualTo("root/branch/leaf"); // full taskUniqueName
            assertThat(taskNode.getId()).isEqualTo(20L);
            assertThat(taskNode.getType()).isEqualTo("plan");
        }

        @Test
        @DisplayName("null fullPath falls back to taskUniqueName")
        void nullFullPathFallsBack() {
            LogLlmTaskDetail d = taskDetail(30L, "req-030", "B", "only-unique-name");
            d.setFullPath(null);

            when(taskDetailRepository.findByRequestIdOrderByTaskIndex("req-030"))
                    .thenReturn(List.of(d));
            when(taskStepRepository.findByDetailIds(List.of(30L))).thenReturn(List.of());
            when(sourceViewerService.mapTaskToLine(any(), any())).thenReturn(null);

            TaskTreeDTO tree = service.buildTaskTree("req-030", null);

            assertThat(tree.getRoots()).hasSize(1);
            assertThat(tree.getRoots().get(0).getName()).isEqualTo("only-unique-name");
        }
    }

    // ─── getTaskSteps ─────────────────────────────────────────────────────────

    @Nested
    class GetTaskSteps {

        @Test
        @DisplayName("delegates to repository and maps step fields")
        void delegatesCorrectly() {
            LogLlmTaskStep s = taskStep(200L, 50L);
            when(taskStepRepository.findByLogLlmTaskDetailIdOrderById(50L))
                    .thenReturn(List.of(s));

            List<TaskStepDTO> result = service.getTaskSteps(50L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(200L);
            assertThat(result.get(0).getStep()).isEqualTo(1);
        }
    }

    // ─── getFailedTasks ───────────────────────────────────────────────────────

    @Nested
    class GetFailedTasks {

        @Test
        @DisplayName("returns empty list when no failed tasks")
        void emptyList() {
            when(taskDetailRepository.findFailedByRequestId("req-fail")).thenReturn(List.of());

            List<TaskDetailDTO> result = service.getFailedTasks("req-fail");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps failed tasks to DTOs")
        void mapsFailedTasks() {
            LogLlmTaskDetail failed = taskDetail(99L, "req-100", "FailingAgent", "failed-task");
            failed.setSuccess(false);
            failed.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
            failed.setTaskEndTime(LocalDateTime.of(2025, 1, 1, 0, 1));

            when(taskDetailRepository.findFailedByRequestId("req-100")).thenReturn(List.of(failed));

            List<TaskDetailDTO> result = service.getFailedTasks("req-100");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSuccess()).isFalse();
            assertThat(result.get(0).getAgentName()).isEqualTo("FailingAgent");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private LogLlmTaskDetail taskDetail(Long id, String requestId, String agentName, String uniqueName) {
        LogLlmTaskDetail d = new LogLlmTaskDetail();
        d.setId(id);
        d.setRequestId(requestId);
        d.setAgentName(agentName);
        d.setTaskName(uniqueName);
        d.setTaskUniqueName(uniqueName);
        d.setTaskType("action");
        d.setTaskIndex(0);
        d.setFullPath(uniqueName);
        d.setSuccess(true);
        d.setResult("ok");
        d.setResultType(1);
        d.setComment(null);
        d.setAgentTryCount(1);
        d.setTaskTryCount(1);
        d.setFinalResult(true);
        d.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        return d;
    }

    private LogLlmTaskStep taskStep(Long id, Long detailId) {
        LogLlmTaskStep s = new LogLlmTaskStep();
        s.setId(id);
        s.setLogLlmTaskDetailId(detailId);
        s.setStep(1);
        s.setTemplate("TestTemplate");
        s.setInput("{\"query\":\"test\"}");
        s.setOutput("{\"result\":\"ok\"}");
        s.setResultType(1);
        s.setSuccess(true);
        s.setEndTime(LocalDateTime.of(2025, 1, 1, 0, 1));
        return s;
    }
}
