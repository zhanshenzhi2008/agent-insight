package com.llm.insight.controller;

import com.llm.insight.dto.response.LogFileDTO;
import com.llm.insight.dto.response.LogSearchResultDTO;
import com.llm.insight.service.LogViewerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogController}.
 * Covers TC-F2 series (TC-F2-01 ~ TC-F2-08).
 */
class LogControllerTest {

    private LogController controller(LogViewerService svc) {
        return new LogController(svc);
    }

    // ─── TC-F2-01: 读取正常日志文件 ────────────────────────────────────────────

    @Nested
    class ReadLogFile {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log?username=xxx returns log content")
        void testReadLog() {
            LogViewerService svc = mock(LogViewerService.class);
            LogFileDTO logFile = LogFileDTO.builder()
                    .fileName("req-001.log").fileSize(10240L).requestId("req-001")
                    .lines(List.of("[INFO] Starting agent", "[DEBUG] Processing data"))
                    .startLine(0).endLine(2).hasMore(false).status("ok")
                    .build();

            when(svc.readLogFile(eq("req-001"), eq("testuser"), eq(0), eq(5000)))
                    .thenReturn(logFile);

            var resp = controller(svc).readLogFile("req-001", "testuser", 0, 5000);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getFileName()).isEqualTo("req-001.log");
            assertThat(resp.getData().getStatus()).isEqualTo("ok");
            assertThat(resp.getData().getLines()).hasSize(2);
            assertThat(resp.getData().getHasMore()).isFalse();
        }
    }

    // ─── TC-F2-02: 日志文件分页读取 ────────────────────────────────────────────

    @Nested
    class ReadLogPaginated {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log?page=0&size=100 returns paginated log")
        void testReadLogPaginated() {
            LogViewerService svc = mock(LogViewerService.class);
            LogFileDTO logFile = LogFileDTO.builder()
                    .fileName("req-001.log").fileSize(100000L).requestId("req-001")
                    .lines(List.of("[INFO] Line 1", "[INFO] Line 2"))
                    .startLine(0).endLine(2).hasMore(true).status("ok")
                    .build();

            when(svc.readLogFile(eq("req-001"), eq("testuser"), eq(0), eq(100)))
                    .thenReturn(logFile);

            var resp = controller(svc).readLogFile("req-001", "testuser", 0, 100);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getHasMore()).isTrue();
            assertThat(resp.getData().getLines()).hasSize(2);
        }
    }

    // ─── TC-F2-03: 日志文件不存在 ──────────────────────────────────────────────

    @Nested
    class LogNotFound {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log returns not_found status")
        void testLogNotFound() {
            LogViewerService svc = mock(LogViewerService.class);
            LogFileDTO notFound = LogFileDTO.notFound("日志文件不可用");

            when(svc.readLogFile(eq("req-missing"), eq("testuser"), anyInt(), anyInt()))
                    .thenReturn(notFound);

            var resp = controller(svc).readLogFile("req-missing", "testuser", 0, 5000);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getStatus()).isEqualTo("not_found");
            assertThat(resp.getData().getMessage()).isEqualTo("日志文件不可用");
        }
    }

    // ─── TC-F2-04: 日志关键词搜索 ──────────────────────────────────────────────

    @Nested
    class SearchLog {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/search?keyword=ERROR returns matches")
        void testSearchLog() {
            LogViewerService svc = mock(LogViewerService.class);
            LogSearchResultDTO result1 = LogSearchResultDTO.builder()
                    .lineNumber(42).lineContent("[ERROR] NullPointerException at line 42").build();
            LogSearchResultDTO result2 = LogSearchResultDTO.builder()
                    .lineNumber(100).lineContent("[ERROR] Connection timeout").build();

            when(svc.searchInLog(eq("req-001"), eq("testuser"), eq("ERROR"), eq(false)))
                    .thenReturn(List.of(result1, result2));

            var resp = controller(svc).searchInLog("req-001", "testuser", "ERROR", false);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getLineNumber()).isEqualTo(42);
            assertThat(resp.getData().get(0).getLineContent()).contains("ERROR");
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/search?keyword=...&regex=true supports regex")
        void testSearchLogRegex() {
            LogViewerService svc = mock(LogViewerService.class);
            LogSearchResultDTO result = LogSearchResultDTO.builder()
                    .lineNumber(50).lineContent("[INFO] Request 123 processed").build();

            when(svc.searchInLog(eq("req-001"), eq("testuser"), eq("\\d+"), eq(true)))
                    .thenReturn(List.of(result));

            var resp = controller(svc).searchInLog("req-001", "testuser", "\\d+", true);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getLineNumber()).isEqualTo(50);
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/search returns empty when no matches")
        void testSearchLogNoMatches() {
            LogViewerService svc = mock(LogViewerService.class);
            when(svc.searchInLog(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(List.of());

            var resp = controller(svc).searchInLog("req-001", "testuser", "NONEXISTENT", false);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ─── TC-F2-08: 日志下载 ────────────────────────────────────────────────────

    @Nested
    class DownloadLog {

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/download returns log file as attachment")
        void testDownloadLog() {
            LogViewerService svc = mock(LogViewerService.class);
            byte[] content = "[INFO] Log content here\n[ERROR] Some error\n".getBytes();
            when(svc.downloadLogFile(eq("req-001"), eq("testuser"))).thenReturn(content);

            ResponseEntity<byte[]> resp = controller(svc).downloadLog("req-001", "testuser");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(content);
            assertThat(resp.getHeaders().getContentType().toString()).contains("text/plain");
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/download returns 404 when file missing")
        void testDownloadLogNotFound() {
            LogViewerService svc = mock(LogViewerService.class);
            when(svc.downloadLogFile(anyString(), anyString())).thenReturn(null);

            ResponseEntity<byte[]> resp = controller(svc).downloadLog("req-missing", "testuser");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /api/v1/requests/{id}/log/download returns 404 when file is empty")
        void testDownloadLogEmpty() {
            LogViewerService svc = mock(LogViewerService.class);
            when(svc.downloadLogFile(anyString(), anyString())).thenReturn(new byte[0]);

            ResponseEntity<byte[]> resp = controller(svc).downloadLog("req-empty", "testuser");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
