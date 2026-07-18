package com.llm.insight.controller;

import com.llm.insight.dto.response.ScriptFileDTO;
import com.llm.insight.dto.response.SourceLineMappingDTO;
import com.llm.insight.service.SourceViewerService;
import com.llm.insight.support.BaseWebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SourceController}.
 *
 * <p>Covers AGENTS.md §6.2 (path traversal red line) by exercising the
 * {@code /scripts/content?path=…} endpoint with absolute paths, traversal
 * sequences, and various agent-name / task-unique-name shapes. Also covers
 * the currently uncovered integration gap: {@link SourceController} had no
 * tests at all prior to this PR.
 */
@DisplayName("SourceController integration (MockMvc standaloneSetup)")
class SourceControllerIntegrationTest extends BaseWebMvcTest {

    private final SourceViewerService sourceViewerService = mock(SourceViewerService.class);

    @Override
    protected Object[] controllerOrAdvice() {
        return new Object[]{new SourceController(sourceViewerService)};
    }

    @Override
    protected Object[] getControllerAdvices() {
        return new Object[]{new GlobalExceptionHandler()};
    }

    // ─── listScripts ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/agents/{agentName}/scripts")
    class ListScripts {

        @Test
        @DisplayName("returns script list envelope")
        void returnsScriptList() throws Exception {
            ScriptFileDTO file = ScriptFileDTO.builder()
                    .fileName("data_agent.java")
                    .fullPath("/var/scripts/DataAgent/data_agent.java")
                    .extension("java")
                    .fileSize(2048L)
                    .lastModified(1719800000000L)
                    .build();
            when(sourceViewerService.listScripts("DataAgent")).thenReturn(List.of(file));

            mockMvc.perform(get("/api/v1/agents/{agentName}/scripts", "DataAgent")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].fileName").value("data_agent.java"))
                    .andExpect(jsonPath("$.data[0].extension").value("java"))
                    .andExpect(jsonPath("$.data[0].fileSize").value(2048))
                    .andExpect(jsonPath("$.data[0].lastModified").value(1719800000000L));

            verify(sourceViewerService).listScripts("DataAgent");
        }

        @Test
        @DisplayName("returns empty data array when no scripts")
        void returnsEmptyWhenNoScripts() throws Exception {
            when(sourceViewerService.listScripts("GhostAgent")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/agents/{agentName}/scripts", "GhostAgent")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── getScriptContent ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/scripts/content (path traversal red line §6.2)")
    class GetScriptContent {

        @Test
        @DisplayName("forwards required path and optional startLine/endLine through to the service")
        void forwardsAllParams() throws Exception {
            when(sourceViewerService.getScriptContent(
                    eq("/var/scripts/DataAgent/main.py"),
                    eq(10),
                    eq(20)))
                    .thenReturn("10|def main():\n11|    pass\n");

            mockMvc.perform(get("/api/v1/scripts/content")
                            .param("path", "/var/scripts/DataAgent/main.py")
                            .param("startLine", "10")
                            .param("endLine", "20")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("10|def main()")));

            verify(sourceViewerService).getScriptContent(
                    eq("/var/scripts/DataAgent/main.py"), eq(10), eq(20));
        }

        @Test
        @DisplayName("treats absent startLine/endLine as null (full content)")
        void omitsOptionalLineParams() throws Exception {
            when(sourceViewerService.getScriptContent(eq("/var/scripts/a.py"), isNull(), isNull()))
                    .thenReturn("full file body");

            mockMvc.perform(get("/api/v1/scripts/content")
                            .param("path", "/var/scripts/a.py")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("full file body"));

            verify(sourceViewerService).getScriptContent(eq("/var/scripts/a.py"), isNull(), isNull());
        }

        @Test
        @DisplayName("path is passed through verbatim — controller does not pre-validate (KNOWN GAP, see §6.2)")
        void pathIsPassedThroughVerbatim() throws Exception {
            // AGENTS.md §6.2 forbids reading user-controlled paths without normalization + whitelist.
            // Currently SourceController forwards `path` straight to the service which then does
            // `Paths.get(scriptPath)` — exposing traversal sequences to the filesystem layer.
            // This test pins the current (vulnerable) behavior so the fix can be delivered as a
            // separate PR without silently drifting. The assertion below documents the gap.
            String traversal = "/var/scripts/../../../../etc/passwd";
            when(sourceViewerService.getScriptContent(eq(traversal), isNull(), isNull()))
                    .thenReturn("/* 服务未做归一化 — 等待 §6.2 修复 */");

            mockMvc.perform(get("/api/v1/scripts/content")
                            .param("path", traversal)
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(sourceViewerService).getScriptContent(eq(traversal), isNull(), isNull());
        }

        @Test
        @DisplayName("missing required path parameter yields 400 via GlobalExceptionHandler")
        void missingRequiredPath() throws Exception {
            mockMvc.perform(get("/api/v1/scripts/content")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── mapTaskToLine ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/agents/{agentName}/scripts/mapping")
    class MapTaskToLine {

        @Test
        @DisplayName("returns mapping envelope when task is found")
        void returnsMapping() throws Exception {
            SourceLineMappingDTO mapping = SourceLineMappingDTO.builder()
                    .agentName("DataAgent")
                    .taskUniqueName("task_analyze")
                    .filePath("/var/scripts/DataAgent/main.py")
                    .startLine(42)
                    .endLine(57)
                    .build();
            when(sourceViewerService.mapTaskToLine("DataAgent", "task_analyze"))
                    .thenReturn(mapping);

            mockMvc.perform(get("/api/v1/agents/{agentName}/scripts/mapping", "DataAgent")
                            .param("taskUniqueName", "task_analyze")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.agentName").value("DataAgent"))
                    .andExpect(jsonPath("$.data.taskUniqueName").value("task_analyze"))
                    .andExpect(jsonPath("$.data.filePath").value("/var/scripts/DataAgent/main.py"))
                    .andExpect(jsonPath("$.data.startLine").value(42))
                    .andExpect(jsonPath("$.data.endLine").value(57));
        }

        @Test
        @DisplayName("returns envelope with null data when service returns null (unmapped task)")
        void returnsNullDataWhenUnmapped() throws Exception {
            when(sourceViewerService.mapTaskToLine("DataAgent", "ghost_task")).thenReturn(null);

            mockMvc.perform(get("/api/v1/agents/{agentName}/scripts/mapping", "DataAgent")
                            .param("taskUniqueName", "ghost_task")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    // data is null — the field exists but with null value
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
    }
}
