package com.llm.insight.explorer.ai;

import com.llm.insight.explorer.engine.ColumnAnalyzerService.AnalyzedColumn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring AI 2.0 多 Provider ChatClient 封装（改造版）。
 *
 * <p>路由策略（优先级从高到低）：
 * <ol>
 *   <li>数据库路由：{@link AiModelRouteService} 按 (capability=CHAT, tier=PRODUCTION)
 *       查到 vendor + instance → {@link DynamicChatModelFactory} 动态构建 ChatModel</li>
 *   <li>降级 fallback：从 application.yml 读 {@code agent-insight.ai.provider/model}，
 *       用预定义的 defaultChatClient bean</li>
 * </ol>
 *
 * <p>缓存：同一 vendor 的 ChatClient 缓存在 {@link DynamicChatClientCache} 中，
 * 页面改了配置后可通过 {@code invalidate(vendorId)} 驱逐。
 */
@Slf4j
@Service
public class AiChatService {

    private final AiProperties aiProps;
    private final AiModelRouteService routeService;
    private final DynamicChatModelFactory chatModelFactory;
    private final DynamicChatClientCache cache;
    /**
     * Fallback：来自 application.yml 的默认 ChatClient（AiChatConfig 创建）。
     * <p>
     * 注意：用 {@link Optional} 包裹，避免在 OpenAI ChatModel 自动配置创建失败时
     * 把整个 AiChatService 拖崩。{@code @Lazy} 让构造函数不强制 bean 初始化，
     * 调用时如果 bean 不存在或创建失败，返回 {@link Optional#empty()}。
     */
    private final ChatClient defaultChatClient;

    @Autowired
    public AiChatService(
            AiProperties aiProps,
            AiModelRouteService routeService,
            DynamicChatModelFactory chatModelFactory,
            DynamicChatClientCache chatClientCache,
            @Qualifier("defaultChatClient")
            @Lazy
            Optional<ChatClient> defaultChatClient) {
        this.aiProps = aiProps;
        this.routeService = routeService;
        this.chatModelFactory = chatModelFactory;
        this.cache = chatClientCache;
        this.defaultChatClient = defaultChatClient.orElse(null);
    }

    /**
     * 获取当前激活的 ChatClient。
     * 优先从数据库路由，降级到 application.yml 配置的默认 OpenAI。
     *
     * @return 激活的 ChatClient；如果 AI 未启用、路由失败、且没有 defaultChatClient 则返回 null
     */
    private ChatClient getClient() {
        if (!aiProps.isEnabled()) {
            return null;
        }

        // 数据库路由优先
        try {
            Optional<AiModelRouteService.RouteResult> routeOpt =
                    routeService.resolve("CHAT", "PRODUCTION");

            if (routeOpt.isPresent()) {
                var route = routeOpt.get();
                Long vendorId = route.vendor().getId();

                ChatClient cached = cache.get(vendorId);
                if (cached != null) {
                    return cached;
                }

                var chatModel = chatModelFactory.create(route.vendor());
                ChatClient client = ChatClient.create(chatModel);
                cache.put(vendorId, client);

                log.info("ChatClient 已构建: vendor={} model={}",
                        route.vendor().getVendor(), route.instance().getModelName());
                return client;
            }
        } catch (Exception e) {
            log.warn("数据库路由失败，降级到默认配置: {}", e.getMessage());
        }

        // 降级：如果 defaultChatClient bean 不存在（如 OpenAI ChatModel 构造失败），
        // 返回 null，让上层方法返回友好提示
        if (defaultChatClient == null) {
            log.warn("无可用数据库配置，且 defaultChatClient 未就绪（OpenAI 自动配置缺失？）");
            return null;
        }

        log.info("无可用数据库配置，使用默认 OpenAI (provider={} model={})",
                aiProps.getProvider(), aiProps.getDefaultModel());
        return defaultChatClient;
    }

    // ===== 公开 API =====

    public String chat(String systemPrompt, String userMessage) {
        ChatClient client = getClient();
        if (client == null) {
            return "# AI 功能未启用\n请在配置中设置 `agent-insight.ai.enabled: true`";
        }

        try {
            String combined = (systemPrompt != null && !systemPrompt.isBlank()
                    ? systemPrompt + "\n\n" : "") + userMessage;

            String response = client.prompt()
                    .user(combined)
                    .call()
                    .content();

            return response != null ? response : "";
        } catch (Exception e) {
            log.error("AI 调用失败: {}", e.getMessage(), e);
            return "# AI 调用失败\n" + e.getMessage();
        }
    }

    public <T> T chatForJson(String systemPrompt, String userMessage, Class<T> clazz) {
        if (!aiProps.isEnabled()) {
            return null;
        }

        ChatClient client = getClient();
        if (client == null) {
            return null;
        }

        try {
            String jsonPrompt = (systemPrompt != null && !systemPrompt.isBlank()
                    ? systemPrompt + "\n\n" : "")
                    + userMessage
                    + "\n\n请仅返回有效的 JSON，不要包含 markdown 代码块包裹。";

            String json = client.prompt()
                    .user(jsonPrompt)
                    .call()
                    .content();

            if (json == null || json.isBlank()) return null;

            // 去掉可能的 ```json 包裹
            json = json.trim();
            if (json.startsWith("```")) {
                int firstNewline = json.indexOf('\n');
                int lastBacktick = json.lastIndexOf("```");
                if (lastBacktick > firstNewline) {
                    json = json.substring(firstNewline, lastBacktick).trim();
                }
            }

            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, clazz);
        } catch (Exception e) {
            log.error("AI JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    // ===== 业务 AI 方法 =====

    public AiColumnAnalysis analyzeColumnWithAi(String columnName, String displayName,
                                               String dataType, String renderType,
                                               List<String> sampleValues,
                                               List<Map<String, Object>> topValues) {
        if (!aiProps.isColumnAnalysisEnabled()) {
            return null;
        }

        String topValsStr = topValues.stream()
                .limit(10)
                .map(m -> m.get("value") + "(" + m.get("ratio") + ")")
                .collect(Collectors.joining(", "));

        String samples = sampleValues.stream()
                .limit(20)
                .map(v -> v == null ? "NULL" : v)
                .collect(Collectors.joining(", "));

        String systemPrompt = """
                你是一个数据配置专家。用户正在配置一个数据浏览器系统，需要你为数据库列推荐最佳展示配置。
                分析列名语义、样本值分布，判断最合适的渲染方式和格式。
                """;

        String userMessage = """
                分析以下数据库列，提供展示配置推荐。

                列名: %s
                当前展示名: %s
                推断数据类型: %s
                当前渲染类型: %s
                样本值（前20个）: %s
                热门值分布: %s

                请以 JSON 格式返回配置推荐：
                {
                  "recommendedRenderType": "TEXT|TAG|BOOLEAN|MONEY|DATE|DATETIME|LINK|JSON|IMAGE|HTML",
                  "recommendedDataType": "STRING|NUMBER|DATETIME|BOOLEAN|JSON|TEXT|ENUM",
                  "reason": "判断理由",
                  "suggestedDisplayName": "建议的中文展示名",
                  "suggestedDateFormat": "如为日期字段填 \\"yyyy-MM-dd HH:mm:ss\\"",
                  "suggestedNumberFormat": "如为数字字段填 \\"#,##0.00\\"",
                  "valueLabels": {"值1": "标签1", "值2": "标签2"},
                  "tagColors": {"值1": "green", "值2": "red"},
                  "isTimeField": true或false,
                  "isHighlight": true或false
                }

                仅返回 JSON，不要 markdown 包裹。
                """.formatted(columnName, displayName, dataType, renderType, samples, topValsStr);

        AiColumnAnalysis result = chatForJson(systemPrompt, userMessage, AiColumnAnalysis.class);
        if (result != null) {
            result.setColumnName(columnName);
        }
        return result;
    }

    public NlQueryResult translateToFilters(String naturalLanguage,
                                           List<AnalyzedColumn> availableColumns) {
        if (!aiProps.isNlQueryEnabled()) {
            return null;
        }

        String columnsJson = availableColumns.stream()
                .map(c -> """
                        {"columnName": "%s", "displayName": "%s", "dataType": "%s", "renderType": "%s"}"""
                        .formatted(c.getColumnName(), c.getDisplayName(),
                                c.getDataType(), c.getRenderType()))
                .collect(Collectors.joining(",\n"));

        String userMessage = """
                可用字段：
                [%s]

                用户需求：%s

                请将上述自然语言转换为查询条件，返回 JSON：
                {
                  "translatedText": "你翻译后的查询描述（中文）",
                  "filters": [
                    {"column": "字段名", "operator": "EQ|NE|GT|GTE|LT|LTE|LIKE|IN|BETWEEN|IS_NULL|IS_NOT_NULL", "value": "查询值", "value2": "BETWEEN时第二个值", "reason": "为什么用这个条件"}
                  ],
                  "sort": {"field": "排序字段", "direction": "ASC|DESC"}
                }

                如果无法转换 filters 返回空数组 []。
                仅返回 JSON，不要 markdown 包裹。
                """.formatted(columnsJson, naturalLanguage);

        return chatForJson("", userMessage, NlQueryResult.class);
    }

    public String summarizeResults(List<Map<String, Object>> rows,
                                 List<AnalyzedColumn> columns,
                                 String tableName,
                                 String userQuestion) {
        if (!aiProps.isSummarizationEnabled() || rows == null || rows.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> sample = rows.size() > 50
                ? rows.subList(0, 50) : rows;

        String columnsJson = columns.stream()
                .map(c -> "%s(%s)".formatted(c.getColumnName(), c.getDisplayName()))
                .collect(Collectors.joining(", "));

        String dataPreview = sample.stream()
                .limit(10)
                .map(row -> row.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(" | ")))
                .collect(Collectors.joining("\n"));

        String systemPrompt = "你是一个数据分析助手。请总结查询结果的关键信息，用简洁的中文描述。";

        String userMessage = """
                用户问题：%s
                查询的表：%s
                返回字段：%s
                数据行数：%d 条
                数据预览（前 10 条）：
                %s

                请用 2-3 句话总结这段数据的关键发现。
                """.formatted(userQuestion, tableName, columnsJson, rows.size(), dataPreview);

        return chat(systemPrompt, userMessage);
    }

    // ===== DTO =====

    @lombok.Data
    public static class AiColumnAnalysis {
        private String columnName;
        private String recommendedRenderType;
        private String recommendedDataType;
        private String reason;
        private String suggestedDisplayName;
        private String suggestedDateFormat;
        private String suggestedNumberFormat;
        private Map<String, String> valueLabels;
        private Map<String, String> tagColors;
        private Boolean isTimeField;
        private Boolean isHighlight;
    }

    @lombok.Data
    public static class NlQueryResult {
        private String translatedText;
        private List<FilterDto> filters;
        private SortDto sort;

        @lombok.Data
        public static class FilterDto {
            private String column;
            private String operator;
            private String value;
            private String value2;
            private String reason;
        }

        @lombok.Data
        public static class SortDto {
            private String field;
            private String direction;
        }
    }
}
