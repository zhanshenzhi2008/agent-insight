package com.llm.insight.explorer.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatClient 缓存，避免每次请求都重新构建 ChatModel。
 *
 * <p>Key = vendorId（Long），Value = ChatClient。
 * 同一 vendor 的 ChatClient 被复用，只有在缓存失效或被驱逐时才重建。
 *
 * <p>线程安全：ConcurrentHashMap + 双重检查锁定（lazy init）。
 *
 * <p>驱逐策略：暂时不用（后续可加 LRU 或按 vendor 更新事件驱逐）。
 */
@Component
public class DynamicChatClientCache {

    private final Map<Long, ChatClient> cache = new ConcurrentHashMap<>();

    /**
     * 根据 vendorId 获取缓存的 ChatClient。
     * 不存在时返回 null（由调用方负责构建）。
     */
    public ChatClient get(Long vendorId) {
        return cache.get(vendorId);
    }

    /**
     * 存入缓存。
     */
    public void put(Long vendorId, ChatClient client) {
        cache.put(vendorId, client);
    }

    /**
     * 让单个 vendor 的缓存失效（页面改了配置时调用）。
     */
    public void invalidate(Long vendorId) {
        cache.remove(vendorId);
    }

    /**
     * 清空全部缓存（全局刷新）。
     */
    public void invalidateAll() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
