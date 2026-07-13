package com.llm.insight.service.impl;

import com.llm.insight.config.InsightProperties;
import com.llm.insight.dto.response.ScriptFileDTO;
import com.llm.insight.dto.response.SourceLineMappingDTO;
import com.llm.insight.service.SourceViewerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class SourceViewerServiceImpl implements SourceViewerService {

    private final InsightProperties properties;

    // 本地索引缓存（内存 LRU）
    private final Map<String, ScriptIndex> indexCache = new LinkedHashMap<>(100) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 200;
        }
    };

    public SourceViewerServiceImpl(InsightProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<ScriptFileDTO> listScripts(String agentName) {
        String scriptRoot = properties.getScript().getRoot();
        Path agentDir = Paths.get(scriptRoot, agentName);

        if (!Files.exists(agentDir) || !Files.isDirectory(agentDir)) {
            return List.of();
        }

        Set<String> extensions = Set.of(properties.getScript().getExtensions().split(","));

        try (Stream<Path> walk = Files.walk(agentDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String ext = getExtension(p);
                        return ext != null && extensions.contains(ext);
                    })
                    .map(p -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                            return ScriptFileDTO.builder()
                                    .fileName(p.getFileName().toString())
                                    .fullPath(p.toAbsolutePath().toString())
                                    .extension(getExtension(p))
                                    .fileSize(attrs.size())
                                    .lastModified(attrs.lastModifiedTime().toMillis())
                                    .build();
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing((ScriptFileDTO s) -> s.getFileName()))
                    .toList();
        } catch (IOException e) {
            log.error("列举脚本文件失败: {}", agentDir, e);
            return List.of();
        }
    }

    @Override
    public String getScriptContent(String scriptPath, Integer startLine, Integer endLine) {
        Path path = Paths.get(scriptPath);
        if (!Files.exists(path)) {
            return "/* 脚本文件不存在: " + scriptPath + " */";
        }

        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);

            if (startLine == null || endLine == null) {
                return String.join("\n", allLines);
            }

            int from = Math.max(0, startLine - 1);
            int to = Math.min(allLines.size(), endLine);

            StringBuilder sb = new StringBuilder();
            for (int i = from; i < to; i++) {
                sb.append(i + 1).append("|").append(allLines.get(i)).append("\n");
            }
            return sb.toString();

        } catch (IOException e) {
            log.error("读取脚本文件失败: {}", scriptPath, e);
            return "/* 脚本文件读取失败: " + e.getMessage() + " */";
        }
    }

    @Override
    public SourceLineMappingDTO mapTaskToLine(String agentName, String taskUniqueName) {
        List<ScriptFileDTO> scripts = listScripts(agentName);

        for (ScriptFileDTO script : scripts) {
            ScriptIndex index = getOrBuildIndex(script.getFullPath());
            Range range = index.getTaskRange(taskUniqueName);
            if (range != null) {
                return SourceLineMappingDTO.builder()
                        .agentName(agentName)
                        .taskUniqueName(taskUniqueName)
                        .filePath(script.getFullPath())
                        .startLine(range.start)
                        .endLine(range.end)
                        .build();
            }
        }
        return null;
    }

    private ScriptIndex getOrBuildIndex(String scriptPath) {
        return indexCache.computeIfAbsent(scriptPath, this::buildIndex);
    }

    private ScriptIndex buildIndex(String scriptPath) {
        ScriptIndex index = new ScriptIndex(scriptPath);

        try {
            String content = Files.readString(Paths.get(scriptPath), StandardCharsets.UTF_8);
            int lineNum = 0;

            for (String line : content.split("\\r?\\n")) {
                lineNum++;

                // 匹配 @Plan(uniqueName="xxx") / @Task(uniqueName="xxx")
                Matcher planMatcher = Pattern.compile(
                        "@Plan\\s*\\([^)]*uniqueName\\s*=\\s*\"([^\"]+)\"",
                        Pattern.CASE_INSENSITIVE).matcher(line);
                if (planMatcher.find()) {
                    index.addTask(planMatcher.group(1), lineNum);
                    continue;
                }

                Matcher taskMatcher = Pattern.compile(
                        "@Task\\s*\\([^)]*uniqueName\\s*=\\s*\"([^\"]+)\"",
                        Pattern.CASE_INSENSITIVE).matcher(line);
                if (taskMatcher.find()) {
                    index.addTask(taskMatcher.group(1), lineNum);
                    continue;
                }

                // 匹配 task("xxx") 或 plan("xxx") 方法调用形式
                Matcher callMatcher = Pattern.compile(
                        "(?:task|plan)\\s*\\(\\s*\"([^\"]+)\"\\s*[,\\)]",
                        Pattern.CASE_INSENSITIVE).matcher(line);
                if (callMatcher.find()) {
                    index.addTask(callMatcher.group(1), lineNum);
                }
            }
        } catch (IOException e) {
            log.error("解析脚本索引失败: {}", scriptPath, e);
        }

        return index;
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : null;
    }

    private static class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class ScriptIndex {
        final String path;
        final Map<String, Range> taskRanges = new LinkedHashMap<>();

        ScriptIndex(String path) {
            this.path = path;
        }

        void addTask(String uniqueName, int startLine) {
            taskRanges.put(uniqueName, new Range(startLine, startLine + 15));
        }

        Range getTaskRange(String uniqueName) {
            return taskRanges.get(uniqueName);
        }
    }
}
