package com.llm.insight.explorer.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent-insight.ai")
public class AiProperties {

    /** 是否启用 AI 功能 */
    private boolean enabled = false;

    /** 默认模型供应商: openai / ollama / azure / minimax */
    private String provider = "openai";

    /** 默认模型 */
    private String defaultModel = "gpt-4o";

    /** 温度参数（0=精确 1=创意） */
    private float temperature = 0.3f;

    /** 最大 token */
    private int maxTokens = 2048;

    /** 执行超时秒数 */
    private int timeoutSeconds = 30;

    /** 是否启用 AI 列分析 */
    private boolean columnAnalysisEnabled = true;

    /** 是否启用自然语言查询 */
    private boolean nlQueryEnabled = true;

    /** 是否启用结果摘要 */
    private boolean summarizationEnabled = true;
}
