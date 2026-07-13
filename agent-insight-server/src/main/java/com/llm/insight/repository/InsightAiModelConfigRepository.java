package com.llm.insight.repository;

import com.llm.insight.repository.entity.InsightAiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsightAiModelConfigRepository extends JpaRepository<InsightAiModelConfig, Long> {

    List<InsightAiModelConfig> findAllByOrderByIdAsc();

    Optional<InsightAiModelConfig> findByVendor(String vendor);
}