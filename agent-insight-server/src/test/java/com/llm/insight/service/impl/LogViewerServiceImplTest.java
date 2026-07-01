package com.llm.insight.service.impl;

import com.llm.insight.config.InsightProperties;
import com.llm.insight.dto.response.LogFileDTO;
import com.llm.insight.dto.response.LogSearchResultDTO;
import com.llm.insight.repository.LogLlmAgentMainRepository;
import com.llm.insight.service.LogViewerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LogViewerServiceImpl}.
 *
 * Verifies:
 *  - readLogFile pages through a real temp file correctly.
 *  - searchInLog finds keyword and regex matches in a real temp file.
 *  - resolveLogFilePath uses DB createTime to compute yyyyMM path.
 *  - downloadLogFile reads full file content.
 *  - not-found / too-large paths return appropriate DTOs.
 */
@ExtendWith(MockitoExtension.class)
class LogViewerServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private InsightProperties properties;

    @Mock
    private LogLlmAgentMainRepository agentMainRepository;

    private LogViewerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogViewerServiceImpl(properties, agentMainRepository);

        InsightProperties.LogConfig logConfig = new InsightProperties.LogConfig();
        logConfig.setDataRoot(tempDir.toString());
        logConfig.setPageSize(3);
        logConfig.setSearchMaxResults(100);
        lenient().when(properties.getLog()).thenReturn(logConfig);
    }

    // ─── readLogFile ───────────────────────────────────────────────────────────

    @Nested
    class ReadLogFile {

        @Test
        @DisplayName("returns not_found DTO when file does not exist")
        void fileNotFound() {
            LogFileDTO result = service.readLogFile("unknown-req", "user1", 0, 10);

            assertThat(result.getStatus()).isEqualTo("not_found");
            assertThat(result.getMessage()).contains("不存在");
        }

        @Test
        @DisplayName("returns too_large DTO when file exceeds 100MB")
        void fileTooLarge() throws IOException {
            // Build file at the path resolveLogFilePath would compute with no DB createTime
            String reqId = "req-big";
            String username = "u_big";
            String expectedMonth = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            Path dir = tempDir.resolve("user").resolve(username).resolve(expectedMonth);
            Files.createDirectories(dir);
            Path bigFile = dir.resolve(reqId + ".log");
            Files.writeString(bigFile, "x".repeat(101 * 1024 * 1024));
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(java.util.Optional.empty());

            LogFileDTO result = service.readLogFile(reqId, username, 0, 10);

            assertThat(result.getStatus()).isEqualTo("too_large");
        }

        @Test
        @DisplayName("returns correct page lines with start/end line numbers")
        void returnsCorrectPage() throws IOException {
            String reqId = "req-page";
            String username = "u1";
            buildLogFile(reqId, username,
                    "line-001", "line-002", "line-003", "line-004", "line-005");
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            LogFileDTO result = service.readLogFile(reqId, username, 1, 2);

            assertThat(result.getStatus()).isEqualTo("ok");
            assertThat(result.getLines()).containsExactly("line-003", "line-004");
            assertThat(result.getStartLine()).isEqualTo(2);
            assertThat(result.getEndLine()).isEqualTo(5);
            assertThat(result.getHasMore()).isTrue();
        }

        @Test
        @DisplayName("last page has hasMore=false")
        void lastPageHasNoMore() throws IOException {
            String reqId = "req-last";
            String username = "u1";
            buildLogFile(reqId, username,
                    "line-001", "line-002", "line-003");
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            LogFileDTO result = service.readLogFile(reqId, username, 0, 3);

            assertThat(result.getStatus()).isEqualTo("ok");
            assertThat(result.getLines()).hasSize(3);
            assertThat(result.getHasMore()).isFalse();
        }

        @Test
        @DisplayName("uses default page size from properties when pageSize is 0")
        void usesDefaultPageSize() throws IOException {
            String reqId = "req-default";
            String username = "u1";
            buildLogFile(reqId, username, "a", "b", "c", "d", "e");
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            // pageSize = 0 → falls back to properties.getLog().getPageSize() = 3
            LogFileDTO result = service.readLogFile(reqId, username, 0, 0);

            assertThat(result.getLines()).hasSize(3); // pageSize=3 from @BeforeEach
        }
    }

    // ─── searchInLog ──────────────────────────────────────────────────────────

    @Nested
    class SearchInLog {

        @Test
        @DisplayName("returns empty list when file not found")
        void emptyWhenFileNotFound() {
            List<LogSearchResultDTO> result = service.searchInLog("unknown", "u", "error", false);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("finds keyword in file (case-insensitive)")
        void findsKeyword() throws IOException {
            String reqId = "req-search";
            String username = "u1";
            buildLogFile(reqId, username,
                    "INFO starting app",
                    "ERROR connection failed",
                    "WARN retry attempt");
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            List<LogSearchResultDTO> result = service.searchInLog(reqId, username, "error", false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLineNumber()).isEqualTo(2);
            assertThat(result.get(0).getLineContent()).contains("ERROR");
        }

        @Test
        @DisplayName("uses regex when regex flag is true")
        void regexSearch() throws IOException {
            String reqId = "req-regex";
            String username = "u1";
            buildLogFile(reqId, username,
                    "error-001: foo",
                    "error-002: bar",
                    "warn: baz");
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            List<LogSearchResultDTO> result = service.searchInLog(reqId, username, "error-\\d+", true);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(LogSearchResultDTO::getLineNumber)
                    .containsExactlyInAnyOrder(1, 2);
        }

        @Test
        @DisplayName("stops after maxResults matches")
        void respectsMaxResults() throws IOException {
            InsightProperties.LogConfig logConfig = new InsightProperties.LogConfig();
            logConfig.setDataRoot(tempDir.toString());
            logConfig.setSearchMaxResults(2);
            when(properties.getLog()).thenReturn(logConfig);

            String reqId = "req-max";
            String username = "u1";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("ERROR line-").append(i).append("\n");
            }
            buildLogFile(reqId, username, sb.toString());
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            List<LogSearchResultDTO> result = service.searchInLog(reqId, username, "ERROR", false);

            assertThat(result).hasSize(2); // limited by searchMaxResults=2
        }
    }

    // ─── resolveLogFilePath ───────────────────────────────────────────────────

    @Nested
    class ResolveLogFilePath {

        @Test
        @DisplayName("uses createTime from DB to determine yyyyMM subdirectory")
        void usesCreateTimeFromDb() {
            LocalDateTime createTime = LocalDateTime.of(2025, 3, 15, 12, 0);
            when(agentMainRepository.findFirstCreateTimeByRequestId("req-time"))
                    .thenReturn(Optional.of(createTime));

            String path = service.resolveLogFilePath("req-time", "alice");

            assertThat(path).contains("202503"); // yyyyMM = 202503
            assertThat(path).contains("req-time.log");
            assertThat(path).contains("alice");
        }

        @Test
        @DisplayName("falls back to current yyyyMM when DB has no createTime")
        void fallsBackToCurrentMonth() {
            when(agentMainRepository.findFirstCreateTimeByRequestId("req-no-time"))
                    .thenReturn(Optional.empty());

            String path = service.resolveLogFilePath("req-no-time", "bob");

            String expectedMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            assertThat(path).contains(expectedMonth);
        }
    }

    // ─── downloadLogFile ──────────────────────────────────────────────────────

    @Nested
    class DownloadLogFile {

        @Test
        @DisplayName("returns empty bytes when file not found")
        void returnsEmptyOnMissingFile() {
            byte[] result = service.downloadLogFile("unknown", "u");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns full file content as bytes")
        void returnsFullContent() throws IOException {
            String reqId = "req-dl";
            String username = "u1";
            String content = "full log content here\nline 2\nline 3";
            buildLogFile(reqId, username, content);
            when(agentMainRepository.findFirstCreateTimeByRequestId(reqId))
                    .thenReturn(Optional.of(LocalDateTime.of(2025, 1, 15, 12, 0)));

            byte[] result = service.downloadLogFile(reqId, username);

            assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Path buildLogFile(String requestId, String username, String... lines) throws IOException {
        return buildLogFile(requestId, username, String.join("\n", lines) + "\n");
    }

    private Path buildLogFile(String requestId, String username, String content) throws IOException {
        Path dir = tempDir.resolve("user").resolve(username).resolve("202501");
        Files.createDirectories(dir);
        Path file = dir.resolve(requestId + ".log");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
