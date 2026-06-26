package com.llm.insight.explorer.ai;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Spring 工具类，用于在非 Spring 管理的对象中获取 Bean。
 * 解决 Service 之间延迟依赖的问题。
 */
@Component
public class SpringHelper {

    private static ApplicationContext context;

    public SpringHelper(ApplicationContext applicationContext) {
        SpringHelper.context = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    public static Object getBean(String name) {
        return context.getBean(name);
    }
}
