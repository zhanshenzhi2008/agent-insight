package com.llm.insight.repository;

import com.llm.insight.repository.entity.LogLlmAgentMain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LogLlmAgentMainRepository
        extends JpaRepository<LogLlmAgentMain, Long>, JpaSpecificationExecutor<LogLlmAgentMain> {

    List<LogLlmAgentMain> findByRequestId(String requestId);

    // Spring Data JPA 方法名自带 LIMIT 1（findFirst/findTop），跨数据库兼容
    @Query("SELECT m FROM LogLlmAgentMain m WHERE m.requestId = :requestId AND m.entranceAgent = true ORDER BY m.id DESC")
    Optional<LogLlmAgentMain> findEntranceByRequestId(@Param("requestId") String requestId);

    @Query("SELECT MIN(m.createTime) FROM LogLlmAgentMain m WHERE m.requestId = :requestId")
    Optional<LocalDateTime> findFirstCreateTimeByRequestId(@Param("requestId") String requestId);

    @Query("SELECT m FROM LogLlmAgentMain m WHERE m.requestId = :requestId ORDER BY m.createTime ASC")
    List<LogLlmAgentMain> findAllByRequestIdOrderByCreateTime(@Param("requestId") String requestId);

    @Query("SELECT m FROM LogLlmAgentMain m WHERE m.requestId = :requestId AND m.agentName = :agentName")
    Optional<LogLlmAgentMain> findByRequestIdAndAgentName(
            @Param("requestId") String requestId,
            @Param("agentName") String agentName);

    @Query("SELECT COUNT(m) FROM LogLlmAgentMain m WHERE m.requestId = :requestId")
    long countByRequestId(@Param("requestId") String requestId);

    @Query("SELECT DISTINCT m.requestId FROM LogLlmAgentMain m WHERE m.requestId LIKE %:keyword%")
    Page<String> findRequestIdsByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
