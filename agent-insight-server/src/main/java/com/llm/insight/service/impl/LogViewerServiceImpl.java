package com.llm.insight.service.impl;

import com.llm.insight.config.InsightProperties;
import com.llm.insight.dto.response.LogFileDTO;
import com.llm.insight.dto.response.LogSearchResultDTO;
import com.llm.insight.repository.LogLlmAgentMainRepository;
import com.llm.insight.service.LogViewerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogViewerServiceImpl implements LogViewerService {

    private final InsightProperties properties;
    private final LogLlmAgentMainRepository agentMainRepository;

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB

    @Override
    public LogFileDTO readLogFile(String requestId, String username, int page, int pageSize) {
        String filePath = resolveLogFilePath(requestId, username);
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            return LogFileDTO.notFound("日志文件不存在或已过期删除");
        }

        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return LogFileDTO.tooLarge(fileSize);
            }

            int actualPageSize = pageSize > 0 ? pageSize : properties.getLog().getPageSize();
            int startLine = page * actualPageSize;
            int endLine = startLine + actualPageSize;

            List<String> lines = new ArrayList<>();
            int currentLine = 0;
            boolean hasMore = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (currentLine > startLine && currentLine <= endLine) {
                        lines.add(line);
                    }
                    if (currentLine > endLine) {
                        hasMore = true;
                        break;
                    }
                }
            }

            LogFileDTO dto = LogFileDTO.builder()
                    .fileName(path.getFileName().toString())
                    .fileSize(fileSize)
                    .requestId(requestId)
                    .lines(lines)
                    .startLine(startLine)
                    .endLine(currentLine)
                    .hasMore(hasMore)
                    .status("ok")
                    .build();

            return dto;

        } catch (IOException e) {
            log.error("读取日志文件失败: {}", filePath, e);
            return LogFileDTO.error("读取日志文件失败: " + e.getMessage());
        }
    }

    @Override
    public List<LogSearchResultDTO> searchInLog(String requestId, String username,
                                                 String keyword, boolean regex) {
        String filePath = resolveLogFilePath(requestId, username);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return List.of();
        }

        Pattern pattern;
        try {
            if (regex) {
                pattern = Pattern.compile(keyword, Pattern.CASE_INSENSITIVE);
            } else {
                pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
            }
        } catch (Exception e) {
            log.warn("无效的正则表达式: {}", keyword);
            return List.of();
        }

        List<LogSearchResultDTO> results = new ArrayList<>();
        int maxResults = properties.getLog().getSearchMaxResults();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            int lineNum = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (pattern.matcher(line).find()) {
                    results.add(LogSearchResultDTO.builder()
                            .lineNumber(lineNum)
                            .lineContent(line)
                            .build());
                    if (results.size() >= maxResults) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.error("搜索日志失败: {}", filePath, e);
        }

        return results;
    }

    @Override
    public String resolveLogFilePath(String requestId, String username) {
        Optional<LocalDateTime> createTimeOpt = agentMainRepository
                .findFirstCreateTimeByRequestId(requestId);

        String yyyyMM;
        if (createTimeOpt.isPresent()) {
            yyyyMM = createTimeOpt.get().format(DateTimeFormatter.ofPattern("yyyyMM"));
        } else {
            yyyyMM = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        }

        String dataRoot = properties.getLog().getDataRoot();
        return String.format("%s/user/%s/%s/%s.log",
                dataRoot, username, yyyyMM, requestId);
    }

    @Override
    public byte[] downloadLogFile(String requestId, String username) {
        String filePath = resolveLogFilePath(requestId, username);
        try {
            return Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            log.error("下载日志文件失败: {}", filePath, e);
            return new byte[0];
        }
    }
}
