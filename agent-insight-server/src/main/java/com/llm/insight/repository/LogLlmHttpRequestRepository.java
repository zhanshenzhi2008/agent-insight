package com.llm.insight.repository;

import com.llm.insight.repository.entity.LogLlmHttpRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogLlmHttpRequestRepository extends JpaRepository<LogLlmHttpRequest, Long> {

    List<LogLlmHttpRequest> findByRequestId(String requestId);

    List<LogLlmHttpRequest> findByRequestIdOrderBySpendTimeDesc(String requestId);

    List<LogLlmHttpRequest> findByRequestIdAndSuccessExpressionFalse(String requestId);

    @Query("SELECT r FROM LogLlmHttpRequest r WHERE r.requestId = :requestId ORDER BY r.spendTime DESC")
    List<LogLlmHttpRequest> findTopByRequestIdOrderBySpendTimeDesc(
            @Param("requestId") String requestId,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM LogLlmHttpRequest r WHERE r.requestId = :requestId")
    long countByRequestId(@Param("requestId") String requestId);

    @Query("SELECT COALESCE(SUM(r.promptTokens), 0) FROM LogLlmHttpRequest r WHERE r.requestId = :requestId")
    int sumPromptTokensByRequestId(@Param("requestId") String requestId);

    @Query("SELECT COALESCE(SUM(r.completionTokens), 0) FROM LogLlmHttpRequest r WHERE r.requestId = :requestId")
    int sumCompletionTokensByRequestId(@Param("requestId") String requestId);
}
