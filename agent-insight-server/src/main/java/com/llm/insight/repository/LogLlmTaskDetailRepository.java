package com.llm.insight.repository;

import com.llm.insight.repository.entity.LogLlmTaskDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogLlmTaskDetailRepository extends JpaRepository<LogLlmTaskDetail, Long> {

    @Query("SELECT d FROM LogLlmTaskDetail d WHERE d.requestId = :requestId ORDER BY d.taskIndex ASC")
    List<LogLlmTaskDetail> findByRequestIdOrderByTaskIndex(@Param("requestId") String requestId);

    @Query("SELECT d FROM LogLlmTaskDetail d WHERE d.requestId = :requestId AND d.agentName = :agentName ORDER BY d.taskIndex ASC")
    List<LogLlmTaskDetail> findByRequestIdAndAgentNameOrderByTaskIndex(
            @Param("requestId") String requestId,
            @Param("agentName") String agentName);

    @Query("SELECT d FROM LogLlmTaskDetail d WHERE d.requestId = :requestId AND d.success = false")
    List<LogLlmTaskDetail> findFailedByRequestId(@Param("requestId") String requestId);

    @Query("SELECT d FROM LogLlmTaskDetail d WHERE d.requestId = :requestId AND d.logLlmAgentMainId = :mainId ORDER BY d.taskIndex ASC")
    List<LogLlmTaskDetail> findByRequestIdAndMainIdOrderByTaskIndex(
            @Param("requestId") String requestId,
            @Param("mainId") Long mainId);

    @Query("SELECT COUNT(d) FROM LogLlmTaskDetail d WHERE d.requestId = :requestId")
    long countByRequestId(@Param("requestId") String requestId);

    @Query("SELECT COUNT(d) FROM LogLlmTaskDetail d WHERE d.requestId = :requestId AND d.success = false")
    long countFailedByRequestId(@Param("requestId") String requestId);

    List<LogLlmTaskDetail> findByLogLlmAgentMainId(Long mainId);
}
