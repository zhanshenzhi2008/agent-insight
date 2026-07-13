package com.llm.insight.repository;

import com.llm.insight.repository.entity.LogLlmTaskStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogLlmTaskStepRepository extends JpaRepository<LogLlmTaskStep, Long> {

    List<LogLlmTaskStep> findByLogLlmTaskDetailIdOrderById(Long logLlmTaskDetailId);

    @Query("SELECT s FROM LogLlmTaskStep s WHERE s.logLlmTaskDetailId IN :detailIds ORDER BY s.id ASC")
    List<LogLlmTaskStep> findByDetailIds(@Param("detailIds") List<Long> detailIds);

    List<LogLlmTaskStep> findByRequestIdOrderById(String requestId);
}
