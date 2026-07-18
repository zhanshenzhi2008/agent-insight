package com.llm.insight.repository;

import com.llm.insight.repository.entity.InsightAiVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsightAiVendorRepository extends JpaRepository<InsightAiVendor, Long> {

    List<InsightAiVendor> findAllByOrderByIdAsc();

    Optional<InsightAiVendor> findByVendor(String vendor);
}