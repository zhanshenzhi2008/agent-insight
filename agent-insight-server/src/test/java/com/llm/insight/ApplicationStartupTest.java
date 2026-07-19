package com.llm.insight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：Spring ApplicationContext 启动行为。
 *
 * <p>核心场景：2026-07-19 云端启动失败 ——
 * "DeepSeek API key must be set"，因为 Spring AI 2.0 的
 * {@code DeepSeekChatAutoConfiguration} 用
 * {@code @ConditionalOnProperty(name = "spring.ai.model.chat",
 *   havingValue = "deepseek", matchIfMissing = true)}，
 * 当这个 key 没配置时默认激活 DeepSeek → 创建 DeepSeekApi → 校验 apiKey 失败。
 *
 * <p>修复策略（验证 application.yml 修复后启动成功）：
 * <ol>
 *   <li>application.yml 用 {@code spring.ai.model.chat: openai} 显式指定</li>
 *   <li>DeepSeek/Anthropic/Google 在 application.yml 中完全移除</li>
 *   <li>AiChatConfig 的 ChatClient Bean 加 @Lazy，不在启动时创建</li>
 * </ol>
 *
 * <p>注意：测试启动时不连接真实数据库/Mongo/Redis，用 exclude 排除非必要 auto-config。
 * 测试不覆盖 spring.ai.* props，让 application.yml 真实生效。
 */
class ApplicationStartupTest {

    @Test
    @DisplayName("application.yml 默认配置启动不炸（修复后）")
    void startupWithApplicationYmlDefaults() {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "0");

        props.put("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
              + "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration,"
              + "org.springdoc.webmvc.core.MultipleOpenApiSupportConfiguration,"
              + "org.springdoc.core.configuration.SpringDocConfiguration");

        // 不覆盖 spring.ai.* — 让 application.yml 真实生效
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AgentInsightApplication.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run()) {

            assertThat(ctx.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("即使 deepseek api-key 是 'EMPTY' 占位也不炸（@Lazy + 移除 spring.ai.model.chat=deepseek 默认激活）")
    void startupWithLegacyDeepSeekEmptyApiKeyShouldNotBlowUp() {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "0");
        // 模拟最坏情况：运维忘了配置，按 yml 默认 "EMPTY" 启动
        props.put("spring.ai.deepseek.api-key", "EMPTY");

        props.put("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.mongo.MongoAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
              + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
              + "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration,"
              + "org.springdoc.webmvc.core.MultipleOpenApiSupportConfiguration,"
              + "org.springdoc.core.configuration.SpringDocConfiguration");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AgentInsightApplication.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run()) {

            assertThat(ctx.isActive()).isTrue();
        }
    }
}
