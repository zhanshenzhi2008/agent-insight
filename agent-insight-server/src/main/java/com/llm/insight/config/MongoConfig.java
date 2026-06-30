package com.llm.insight.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexCreator;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * MongoDB 配置。
 *
 * <h3>1. 自动建索引</h3>
 * Spring Boot 4 起 {@code spring.data.mongodb.auto-index-creation} 配置项已被移除，
 * 同时 {@code DataMongoConfiguration} 不再自动注册 {@link MongoPersistentEntityIndexCreator}，
 * 因此默认是关闭的。这里显式注册该 Bean 来保持与原
 * {@code auto-index-creation: true} 一致的语义：启动时自动为带
 * {@code @Indexed} / {@code @CompoundIndex} 等注解的实体创建索引。
 *
 * <h3>2. JSR-310 (java.time) 使用 native codec</h3>
 * Spring Data MongoDB 默认的 JSR-310 converter 使用本机时区（系统默认时区）作为基准，
 * 跨时区部署时容易产生数据不一致。<br>
 * 官方建议改用 MongoDB 原生驱动 codec，以 UTC 为基准序列化 {@code LocalDate} /
 * {@code LocalDateTime} / {@code LocalTime}，详见：
 * <a href="https://docs.spring.io/spring-data/mongodb/reference/mongodb/mapping/mapping.html">
 * Object Mapping - Java Time Types</a>。
 *
 * <p>{@link MongoMappingContext} 与 {@link MongoCustomConversions} 原本由 Spring Boot 的
 * {@code DataMongoConfiguration} 提供。我们这里自定义 {@link MongoCustomConversions}（覆盖默认），
 * 同时保留对 {@link MongoMappingContext} 的注入。</p>
 */
@Configuration
public class MongoConfig {

    /**
     * 启动时自动建索引。
     * 监听 MappingContextEvent，逐个实体解析 @Indexed/@CompoundIndex 注解并创建索引。
     */
    @Bean
    public MongoPersistentEntityIndexCreator mongoPersistentEntityIndexCreator(
            MongoMappingContext mongoMappingContext,
            MongoTemplate mongoTemplate,
            ObjectProvider<MongoPersistentEntityIndexResolver> resolverProvider) {
        MongoPersistentEntityIndexResolver resolver = resolverProvider.getIfAvailable(
                () -> new MongoPersistentEntityIndexResolver(mongoMappingContext));
        return new MongoPersistentEntityIndexCreator(mongoMappingContext, mongoTemplate, resolver);
    }

    /**
     * 自定义 MongoCustomConversions：使用 MongoDB 原生驱动 codec 处理 JSR-310，
     * 以 UTC 为基准序列化 LocalDate / LocalDateTime / LocalTime。
     *
     * <p>覆盖 Spring Boot {@code DataMongoConfiguration.mongoCustomConversions()} 的默认注册，
     * 效果与官方推荐配置一致：所有 {@code java.time} 类型以 UTC 时间戳形式存入 MongoDB。</p>
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(config -> config.useNativeDriverJavaTimeCodecs());
    }
}