package com.llm.insight.repository;

import com.llm.insight.repository.entity.InsightModelInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsightModelInstanceRepository extends JpaRepository<InsightModelInstance, Long> {

    List<InsightModelInstance> findAllByOrderByIdAsc();

    List<InsightModelInstance> findByVendorIdOrderByIdAsc(Long vendorId);

    List<InsightModelInstance> findByCapabilityOrderByPriorityAsc(String capability);

    /**
     * 路由核心查询：在指定 (capability, tier) 下拿当前激活且优先级最高的实例。
     * 路由策略：is_current=1 > priority 升序。
     */
    Optional<InsightModelInstance> findFirstByCapabilityAndTierAndIsCurrentOrderByPriorityAsc(
            String capability, String tier, Integer isCurrent);

    /**
     * 在指定 (capability, tier) 下拿所有激活的实例，按 priority 升序。
     * 用于同 tier 内多个候选的兜底路由。
     */
    List<InsightModelInstance> findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(
            String capability, String tier, Integer isActive);
}