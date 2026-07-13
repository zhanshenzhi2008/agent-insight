package com.llm.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent-insight")
public class InsightProperties {

    private LogConfig log = new LogConfig();
    private ScriptConfig script = new ScriptConfig();
    private CacheConfig cache = new CacheConfig();

    @Data
    public static class LogConfig {
        private String dataRoot = "/app/project/data";
        private String fileSuffix = ".log";
        private int largeFileThresholdMb = 5;
        private int pageSize = 5000;
        private int searchMaxResults = 500;
    }

    @Data
    public static class ScriptConfig {
        private String root = "/app/project/data/system/agt";
        private String extensions = "java,py,md";
    }

    @Data
    public static class CacheConfig {
        private int analysisTtlMinutes = 30;
        private int indexTtlHours = 24;
    }
}
