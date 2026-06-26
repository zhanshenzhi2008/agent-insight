package com.llm.insight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Agent Insight API")
                .version("1.0.0")
                .description("Agent 执行链路分析引擎 REST API")
                .contact(new Contact().name("Agent Platform Team")));
    }
}
