package com.llm.insight.explorer.ai;

import com.llm.insight.explorer.service.InsightModelInstanceService;
import com.llm.insight.repository.entity.InsightAiVendor;
import com.llm.insight.repository.entity.InsightModelInstance;
import com.llm.insight.repository.InsightAiVendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AI 模型路由服务。
 * <p>
 * 从 {@code insight_model_instance} + {@code insight_ai_vendor} 双表中查找当前
 * 激活的模型实例 + 凭证，供后续 ChatClient 装配使用。
 * <p>
 * 路由规则：
 * <ol>
 *   <li>先按 (capability, tier, is_current=1) 查找</li>
 *   <li>找不到则按 (capability, tier, is_active=1, priority ASC) 兜底</li>
 *   <li>找不到返回 empty（调用方降级）</li>
 * </ol>
 *
 * <p>当前用法：仅提供查询入口，未来 {@code AiChatService} 可基于此切换 ChatModel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelRouteService {

    private final InsightModelInstanceService instanceService;
    private final InsightAiVendorRepository vendorRepository;

    /**
     * 路由结果：vendor 凭证 + model instance 元数据。
     */
    public record RouteResult(InsightAiVendor vendor, InsightModelInstance instance) {}

    /**
     * 解析 (capability, tier) 下当前激活的模型。
     */
    public Optional<RouteResult> resolve(String capability, String tier) {
        Optional<InsightModelInstance> instOpt = instanceService.resolveCurrent(capability, tier);
        if (instOpt.isEmpty()) {
            log.debug("无可用模型: capability={} tier={}", capability, tier);
            return Optional.empty();
        }
        InsightModelInstance inst = instOpt.get();
        Optional<InsightAiVendor> vendorOpt = vendorRepository.findById(inst.getVendorId());
        if (vendorOpt.isEmpty()) {
            log.warn("模型实例 {} 关联 vendor {} 不存在", inst.getId(), inst.getVendorId());
            return Optional.empty();
        }
        return Optional.of(new RouteResult(vendorOpt.get(), inst));
    }
}