package com.llm.insight.explorer.service;

import com.llm.insight.common.SnowflakeIdGenerator;
import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.dto.AiVendorRequest;
import com.llm.insight.dto.AiVendorResponse;
import com.llm.insight.repository.InsightAiVendorRepository;
import com.llm.insight.repository.entity.InsightAiVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型供应商（凭证层）服务。
 * <p>
 * 一个 vendor 唯一一条记录，凭证（base_url / token / api_version / proxy / timeout）
 * 集中管理。模型实例（{@link com.llm.insight.repository.entity.InsightModelInstance}）
 * 通过 vendorId 关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAiVendorService {

    private final InsightAiVendorRepository repository;
    private final TokenEncryptor tokenEncryptor;

    public List<AiVendorResponse> list() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(AiVendorResponse::from)
                .toList();
    }

    public Optional<AiVendorResponse> getById(Long id) {
        return repository.findById(id).map(AiVendorResponse::from);
    }

    public Optional<AiVendorResponse> getByVendor(String vendor) {
        return repository.findByVendor(vendor).map(AiVendorResponse::from);
    }

    @Transactional
    public AiVendorResponse create(AiVendorRequest req) {
        if (repository.findByVendor(req.getVendor()).isPresent()) {
            throw new IllegalArgumentException("vendor 已存在: " + req.getVendor());
        }
        InsightAiVendor e = new InsightAiVendor();
        e.setId(SnowflakeIdGenerator.next());
        e.setVendor(req.getVendor());
        e.setDisplayName(orDefault(req.getDisplayName(), req.getVendor()));
        e.setBaseUrl(req.getBaseUrl());
        e.setApiVersion(req.getApiVersion());
        e.setProxyHost(req.getProxyHost());
        e.setProxyPort(req.getProxyPort());
        e.setTimeoutSeconds(orDefault(req.getTimeoutSeconds(), 30));
        e.setMaxRetries(orDefault(req.getMaxRetries(), 3));
        e.setExtraConfig(req.getExtraConfig());
        e.setStatus(req.getStatus());
        e.setDescription(req.getDescription());
        applyTokenIfPresent(e, req.getToken());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        try {
            InsightAiVendor saved = repository.save(e);
            log.info("创建 AI vendor id={} vendor={}", saved.getId(), saved.getVendor());
            return AiVendorResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("vendor 已存在或字段非法: " + ex.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    public AiVendorResponse update(Long id, AiVendorRequest req) {
        InsightAiVendor e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("vendor 不存在: id=" + id));
        if (!e.getVendor().equals(req.getVendor())
                && repository.findByVendor(req.getVendor()).isPresent()) {
            throw new IllegalArgumentException("vendor 已存在: " + req.getVendor());
        }
        e.setVendor(req.getVendor());
        e.setDisplayName(req.getDisplayName());
        e.setBaseUrl(req.getBaseUrl());
        e.setApiVersion(req.getApiVersion());
        e.setProxyHost(req.getProxyHost());
        e.setProxyPort(req.getProxyPort());
        if (req.getTimeoutSeconds() != null) e.setTimeoutSeconds(req.getTimeoutSeconds());
        if (req.getMaxRetries() != null) e.setMaxRetries(req.getMaxRetries());
        e.setExtraConfig(req.getExtraConfig());
        e.setStatus(req.getStatus());
        e.setDescription(req.getDescription());
        applyTokenIfPresent(e, req.getToken());
        e.setUpdateTime(LocalDateTime.now());
        log.info("更新 AI vendor id={} vendor={}", e.getId(), e.getVendor());
        return AiVendorResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("vendor 不存在: id=" + id);
        }
        repository.deleteById(id);
        log.info("删除 AI vendor id={}", id);
    }

    /**
     * 解密出明文 token（供 Spring AI 运行时使用）。
     * 仅限 Service / Engine 层内部调用，绝不暴露到 Controller。
     */
    public String decryptToken(Long id) {
        InsightAiVendor e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("vendor 不存在: id=" + id));
        if (e.getTokenEncrypted() == null || e.getTokenEncrypted().length == 0) {
            return null;
        }
        String encoded = new String(e.getTokenEncrypted(), StandardCharsets.UTF_8);
        return tokenEncryptor.decrypt(encoded);
    }

    private void applyTokenIfPresent(InsightAiVendor e, String token) {
        if (token == null || token.isBlank() || "******".equals(token)) {
            return;
        }
        String encrypted = tokenEncryptor.encrypt(token);
        e.setTokenEncrypted(encrypted.getBytes(StandardCharsets.UTF_8));
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static Integer orDefault(Integer v, Integer def) {
        return v == null ? def : v;
    }
}