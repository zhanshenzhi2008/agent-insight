package com.llm.insight.controller;

import com.llm.insight.dto.response.LogFileDTO;
import com.llm.insight.dto.response.LogSearchResultDTO;
import com.llm.insight.service.LogViewerService;
import com.llm.insight.support.BaseWebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link LogController}.
 *
 * <p>Mirrors {@code TraceControllerIntegrationTest}: uses
 * {@code MockMvcBuilders.standaloneSetup} via {@link BaseWebMvcTest} so the
 * real Spring MVC serialization layer (Jackson, UTF-8, {@code @PathVariable},
 * {@code @RequestParam}, {@code @RestControllerAdvice}) is exercised. The pure
 * Mockito {@code LogControllerTest} does not cover HTTP-level wiring, query
 * parameter defaults, or the {@code GlobalExceptionHandler} envelope.
 *
 * <p>Test IDs use {@code TC-F2-INT} prefix to distinguish from the unit-test
 * {@code TC-F2} series.
 */
@DisplayName("LogController integration (MockMvc standaloneSetup)")
class LogControllerIntegrationTest extends BaseWebMvcTest {

    private final LogViewerService logViewerService = mock(LogViewerService.class);

    @Override
    protected Object[] controllerOrAdvice() {
        return new Object[]{new LogController(logViewerService)};
    }

    @Override
    protected Object[] getControllerAdvices() {
        return new Object[]{new GlobalExceptionHandler()};
    }

    // ─── TC-F2-INT-01: readLogFile ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/log")
    class ReadLogFile {

        @Test
        @DisplayName("returns 200 + envelope with log content (default pageSize=5000)")
        void readsLogWithDefaults() throws Exception {
            LogFileDTO dto = LogFileDTO.builder()
                    .fileName("req-001.log").fileSize(10240L).requestId("req-001")
                    .lines(List.of("[INFO] start", "[DEBUG] work"))
                    .startLine(0).endLine(2).hasMore(false).status("ok")
                    .build();
            when(logViewerService.readLogFile(eq("req-001"), eq("alice"), eq(0), eq(5000)))
                    .thenReturn(dto);

            mockMvc.perform(get("/api/v1/requests/{requestId}/log", "req-001")
                            .param("username", "alice")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data.fileName").value("req-001.log"))
                    .andExpect(jsonPath("$.data.requestId").value("req-001"))
                    .andExpect(jsonPath("$.data.status").value("ok"))
                    .andExpect(jsonPath("$.data.hasMore").value(false))
                    .andExpect(jsonPath("$.data.lines", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.data.lines[0]").value("[INFO] start"));

            verify(logViewerService).readLogFile(eq("req-001"), eq("alice"), eq(0), eq(5000));
        }

        @Test
        @DisplayName("honors explicit page and pageSize query params")
        void honorsExplicitPagination() throws Exception {
            LogFileDTO dto = LogFileDTO.builder()
                    .fileName("req-big.log").fileSize(999_999L).requestId("req-big")
                    .lines(List.of("[INFO] line 100", "[INFO] line 101"))
                    .startLine(100).endLine(102).hasMore(true).status("ok")
                    .build();
            when(logViewerService.readLogFile(eq("req-big"), eq("bob"), eq(2), eq(50)))
                    .thenReturn(dto);

            mockMvc.perform(get("/api/v1/requests/{requestId}/log", "req-big")
                            .param("username", "bob")
                            .param("page", "2")
                            .param("pageSize", "50")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.hasMore").value(true))
                    .andExpect(jsonPath("$.data.startLine").value(100));

            verify(logViewerService).readLogFile(eq("req-big"), eq("bob"), eq(2), eq(50));
        }

        @Test
        @DisplayName("forwards not_found status when log file is missing")
        void forwardsNotFoundStatus() throws Exception {
            when(logViewerService.readLogFile(eq("req-missing"), eq("alice"), anyInt(), anyInt()))
                    .thenReturn(LogFileDTO.notFound("日志文件不可用"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/log", "req-missing")
                            .param("username", "alice")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("not_found"))
                    .andExpect(jsonPath("$.data.message").value("日志文件不可用"))
                    .andExpect(jsonPath("$.data.lines").doesNotExist());
        }

        @Test
        @DisplayName("missing required username → 400 via GlobalExceptionHandler")
        void rejectsMissingUsername() throws Exception {
            mockMvc.perform(get("/api/v1/requests/{requestId}/log", "req-001")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message", containsString("缺少必填参数")))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    // ─── TC-F2-INT-02: searchInLog ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/log/search")
    class SearchInLog {

        @Test
        @DisplayName("returns matches with literal keyword (regex=false default)")
        void searchesWithDefaultRegexFalse() throws Exception {
            LogSearchResultDTO r1 = LogSearchResultDTO.builder()
                    .lineNumber(42).lineContent("[ERROR] NPE at line 42").build();
            LogSearchResultDTO r2 = LogSearchResultDTO.builder()
                    .lineNumber(100).lineContent("[ERROR] Connection timeout").build();
            when(logViewerService.searchInLog(eq("req-001"), eq("alice"), eq("ERROR"), eq(false)))
                    .thenReturn(List.of(r1, r2));

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/search", "req-001")
                            .param("username", "alice")
                            .param("keyword", "ERROR")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.data[0].lineNumber").value(42))
                    .andExpect(jsonPath("$.data[0].lineContent", containsString("ERROR")))
                    .andExpect(jsonPath("$.data[1].lineNumber").value(100));

            verify(logViewerService).searchInLog(eq("req-001"), eq("alice"), eq("ERROR"), eq(false));
        }

        @Test
        @DisplayName("passes regex=true through when supplied")
        void honorsRegexTrue() throws Exception {
            LogSearchResultDTO r = LogSearchResultDTO.builder()
                    .lineNumber(50).lineContent("[INFO] request 123 processed").build();
            when(logViewerService.searchInLog(eq("req-001"), eq("alice"), eq("\\d+"), eq(true)))
                    .thenReturn(List.of(r));

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/search", "req-001")
                            .param("username", "alice")
                            .param("keyword", "\\d+")
                            .param("regex", "true")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].lineNumber").value(50));

            verify(logViewerService).searchInLog(eq("req-001"), eq("alice"), eq("\\d+"), eq(true));
        }

        @Test
        @DisplayName("returns empty data array when no matches")
        void returnsEmptyOnNoMatch() throws Exception {
            when(logViewerService.searchInLog(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/search", "req-001")
                            .param("username", "alice")
                            .param("keyword", "NEVER_MATCHES")
                            .accept("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("missing required keyword → 400 via GlobalExceptionHandler")
        void rejectsMissingKeyword() throws Exception {
            mockMvc.perform(get("/api/v1/requests/{requestId}/log/search", "req-001")
                            .param("username", "alice")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ─── TC-F2-INT-03: downloadLog ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/requests/{requestId}/log/download")
    class DownloadLog {

        @Test
        @DisplayName("returns 200 with Content-Disposition attachment and text/plain body")
        void downloadsLogAsAttachment() throws Exception {
            byte[] body = "[INFO] hello\n[ERROR] boom\n".getBytes();
            when(logViewerService.downloadLogFile(eq("req-001"), eq("alice"))).thenReturn(body);

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/download", "req-001")
                            .param("username", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            containsString("attachment")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            containsString("req-001.log")))
                    .andExpect(content().contentTypeCompatibleWith("text/plain"))
                    .andExpect(content().bytes(body));

            verify(logViewerService).downloadLogFile(eq("req-001"), eq("alice"));
        }

        @Test
        @DisplayName("returns 404 when service returns null")
        void returns404WhenNull() throws Exception {
            when(logViewerService.downloadLogFile(eq("req-missing"), eq("alice"))).thenReturn(null);

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/download", "req-missing")
                            .param("username", "alice"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 404 when service returns empty byte array")
        void returns404WhenEmpty() throws Exception {
            when(logViewerService.downloadLogFile(eq("req-empty"), eq("alice")))
                    .thenReturn(new byte[0]);

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/download", "req-empty")
                            .param("username", "alice"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── TC-F2-INT-04: error envelope ──────────────────────────────────────────

    @Nested
    @DisplayName("error handling")
    class ErrorEnvelope {

        @Test
        @DisplayName("IllegalArgumentException → 400 envelope (GlobalExceptionHandler)")
        void mapsIllegalArgumentTo400() throws Exception {
            when(logViewerService.readLogFile(eq("bad"), eq("alice"), anyInt(), anyInt()))
                    .thenThrow(new IllegalArgumentException("username 不能为空"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/log", "bad")
                            .param("username", "alice")
                            .accept("application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("username 不能为空"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("RuntimeException → 500 envelope")
        void mapsUnexpectedTo500() throws Exception {
            when(logViewerService.searchInLog(eq("boom"), eq("alice"), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("磁盘故障"));

            mockMvc.perform(get("/api/v1/requests/{requestId}/log/search", "boom")
                            .param("username", "alice")
                            .param("keyword", "x")
                            .accept("application/json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message", containsString("磁盘故障")));
        }
    }
}