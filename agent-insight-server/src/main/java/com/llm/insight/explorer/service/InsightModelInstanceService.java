package com.llm.insight.explorer.service;

import com.llm.insight.common.SnowflakeIdGenerator;
import com.llm.insight.dto.AiModelInstanceRequest;
import com.llm.insight.dto.AiModelInstanceResponse;
import com.llm.insight.repository.InsightAiVendorRepository;
import com.llm.insight.repository.InsightModelInstanceRepository;
import com.llm.insight.repository.entity.InsightAiVendor;
import com.llm.insight.repository.entity.InsightModelInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 模型实例服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>CRUD 模型实例</li>
 *   <li>路由：按 (capability, tier, is_current) 找当前激活的实例</li>
 *   <li>批量列出（含 vendor 名映射）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightModelInstanceService {

    public static final String CAPABILITY_CHAT = "CHAT";
    public static final String CAPABILITY_EMBEDDING = "EMBEDDING";
    public static final String TIER_PRODUCTION = "PRODUCTION";
    public static final String TIER_LIGHT = "LIGHT";
    public static final String TIER_EXPERIMENTAL = "EXPERIMENTAL";

    private final InsightModelInstanceRepository instanceRepository;
    private final InsightAiVendorRepository vendorRepository;

    // ------------------------------------------------------------------
    // 列表 / 查询
    // ------------------------------------------------------------------

    public List<AiModelInstanceResponse> list() {
        return listWithVendorName(instanceRepository.findAllByOrderByIdAsc());
    }

    public List<AiModelInstanceResponse> listByVendor(Long vendorId) {
        return listWithVendorName(instanceRepository.findByVendorIdOrderByIdAsc(vendorId));
    }

    public List<AiModelInstanceResponse> listByCapability(String capability) {
        return listWithVendorName(instanceRepository.findByCapabilityOrderByPriorityAsc(capability));
    }

    public Optional<AiModelInstanceResponse> getById(Long id) {
        return instanceRepository.findById(id)
                .map(this::toResponseWithVendorName);
    }

    /**
     * 路由核心：在指定 (capability, tier) 下拿当前激活且优先级最高的实例。
     * 优先 is_current=1，回退到 is_active=1。
     */
    public Optional<InsightModelInstance> resolveCurrent(String capability, String tier) {
        Optional<InsightModelInstance> cur = instanceRepository
                .findFirstByCapabilityAndTierAndIsCurrentOrderByPriorityAsc(capability, tier, 1);
        if (cur.isPresent()) return cur;
        return instanceRepository
                .findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(capability, tier, 1)
                .stream().findFirst();
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @Transactional
    public AiModelInstanceResponse create(AiModelInstanceRequest req) {
        validateVendorExists(req.getVendorId());
        validateCapabilityTier(req.getCapability(), req.getTier());

        InsightModelInstance e = new InsightModelInstance();
        e.setId(SnowflakeIdGenerator.next());
        e.setVendorId(req.getVendorId());
        e.setModelName(req.getModelName());
        e.setDeploymentName(req.getDeploymentName());
        e.setCapability(req.getCapability());
        e.setTier(req.getTier());
        e.setPriority(req.getPriority() == null ? 1 : req.getPriority());
        e.setMaxTokens(req.getMaxTokens());
        e.setTemperature(req.getTemperature());
        e.setTopP(req.getTopP());
        e.setIsActive(req.getIsActive() == null ? 1 : req.getIsActive());
        e.setIsCurrent(req.getIsCurrent() == null ? 0 : req.getIsCurrent());
        e.setDescription(req.getDescription());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());

        if (e.getIsCurrent() == 1) {
            demoteOtherCurrent(e.getCapability(), e.getTier(), e.getId());
        }

        try {
            InsightModelInstance saved = instanceRepository.save(e);
            log.info("创建 model instance id={} vendor={} model={} capability={} tier={}",
                    saved.getId(), saved.getVendorId(), saved.getModelName(),
                    saved.getCapability(), saved.getTier());
            return toResponseWithVendorName(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("已存在同 vendor+model+capability 的实例: "
                    + ex.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    public AiModelInstanceResponse update(Long id, AiModelInstanceRequest req) {
        InsightModelInstance e = instanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型实例不存在: id=" + id));
        validateVendorExists(req.getVendorId());
        validateCapabilityTier(req.getCapability(), req.getTier());

        e.setVendorId(req.getVendorId());
        e.setModelName(req.getModelName());
        e.setDeploymentName(req.getDeploymentName());
        e.setCapability(req.getCapability());
        e.setTier(req.getTier());
        if (req.getPriority() != null) e.setPriority(req.getPriority());
        e.setMaxTokens(req.getMaxTokens());
        e.setTemperature(req.getTemperature());
        e.setTopP(req.getTopP());
        if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
        if (req.getIsCurrent() != null) e.setIsCurrent(req.getIsCurrent());
        e.setDescription(req.getDescription());
        e.setUpdateTime(LocalDateTime.now());

        if (e.getIsCurrent() == 1) {
            demoteOtherCurrent(e.getCapability(), e.getTier(), e.getId());
        }

        log.info("更新 model instance id={} model={}", e.getId(), e.getModelName());
        return toResponseWithVendorName(instanceRepository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!instanceRepository.existsById(id)) {
            throw new IllegalArgumentException("模型实例不存在: id=" + id);
        }
        instanceRepository.deleteById(id);
        log.info("删除 model instance id={}", id);
    }

    // ------------------------------------------------------------------
    // 路由辅助
    // ------------------------------------------------------------------

    /**
     * 把同 (capability, tier) 下其它实例的 is_current 降为 0。
     * 保证路由时只可能选中一个实例。
     */
    private void demoteOtherCurrent(String capability, String tier, Long keepId) {
        List<InsightModelInstance> others =
                instanceRepository.findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(
                        capability, tier, 1);
        for (InsightModelInstance o : others) {
            if (!o.getId().equals(keepId) && o.getIsCurrent() != null && o.getIsCurrent() == 1) {
                o.setIsCurrent(0);
                o.setUpdateTime(LocalDateTime.now());
                instanceRepository.save(o);
            }
        }
    }

    private void validateVendorExists(Long vendorId) {
        if (!vendorRepository.existsById(vendorId)) {
            throw new IllegalArgumentException("vendor 不存在: id=" + vendorId);
        }
    }

    private void validateCapabilityTier(String capability, String tier) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("capability 不能为空");
        }
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // 响应映射（含 vendor 名）
    // ------------------------------------------------------------------

    private List<AiModelInstanceResponse> listWithVendorName(List<InsightModelInstance> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, String> vendorNames = vendorRepository.findAllById(
                list.stream().map(InsightModelInstance::getVendorId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(InsightAiVendor::getId, InsightAiVendor::getVendor));

        return list.stream()
                .map(e -> AiModelInstanceResponse.from(e, vendorNames.get(e.getVendorId())))
                .toList();
    }

    private AiModelInstanceResponse toResponseWithVendorName(InsightModelInstance e) {
        String name = vendorRepository.findById(e.getVendorId())
                .map(InsightAiVendor::getVendor)
                .orElse(null);
        return AiModelInstanceResponse.from(e, name);
    }
}