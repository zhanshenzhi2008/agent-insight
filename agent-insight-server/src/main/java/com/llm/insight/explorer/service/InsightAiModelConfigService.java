package com.llm.insight.explorer.service;

import com.llm.insight.config.TokenEncryptor;
import com.llm.insight.dto.AiModelConfigRequest;
import com.llm.insight.dto.AiModelConfigResponse;
import com.llm.insight.repository.InsightAiModelConfigRepository;
import com.llm.insight.repository.entity.InsightAiModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型供应商配置服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>列表查询（脱敏）</li>
 *   <li>按 ID/按 vendor 获取（脱敏）</li>
 *   <li>创建 / 更新 / 删除</li>
 *   <li>token 明文 ↔ 密文转换</li>
 * </ul>
 *
 * <p>表只有 4-8 行极小数据量，**不**做缓存、不**分**页，简单 list+save 即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAiModelConfigService {

    private final InsightAiModelConfigRepository repository;
    private final TokenEncryptor tokenEncryptor;

    public List<AiModelConfigResponse> list() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(AiModelConfigResponse::from)
                .toList();
    }

    public Optional<AiModelConfigResponse> getById(Long id) {
        return repository.findById(id).map(AiModelConfigResponse::from);
    }

    public Optional<AiModelConfigResponse> getByVendor(String vendor) {
        return repository.findByVendor(vendor).map(AiModelConfigResponse::from);
    }

    @Transactional
    public AiModelConfigResponse create(AiModelConfigRequest req) {
        if (repository.findByVendor(req.getVendor()).isPresent()) {
            throw new IllegalArgumentException("vendor 已存在: " + req.getVendor());
        }
        InsightAiModelConfig e = new InsightAiModelConfig();
        e.setId(generateSnowflakeId());
        e.setVendor(req.getVendor());
        e.setModels(req.getModels());
        e.setBaseUrl(req.getBaseUrl());
        e.setStatus(req.getStatus());
        e.setDescription(req.getDescription());
        e.setTemperature(req.getTemperature());
        applyTokenIfPresent(e, req.getToken());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        try {
            InsightAiModelConfig saved = repository.save(e);
            log.info("创建 AI 模型配置 id={} vendor={}", saved.getId(), saved.getVendor());
            return AiModelConfigResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("vendor 已存在或字段非法: " + ex.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    public AiModelConfigResponse update(Long id, AiModelConfigRequest req) {
        InsightAiModelConfig e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: id=" + id));
        // vendor 唯一约束：若修改了 vendor，先检查是否冲突
        if (!e.getVendor().equals(req.getVendor())
                && repository.findByVendor(req.getVendor()).isPresent()) {
            throw new IllegalArgumentException("vendor 已存在: " + req.getVendor());
        }
        e.setVendor(req.getVendor());
        e.setModels(req.getModels());
        e.setBaseUrl(req.getBaseUrl());
        e.setStatus(req.getStatus());
        e.setDescription(req.getDescription());
        e.setTemperature(req.getTemperature());
        applyTokenIfPresent(e, req.getToken());
        e.setUpdateTime(LocalDateTime.now());
        log.info("更新 AI 模型配置 id={} vendor={}", e.getId(), e.getVendor());
        return AiModelConfigResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("配置不存在: id=" + id);
        }
        repository.deleteById(id);
        log.info("删除 AI 模型配置 id={}", id);
    }

    /**
     * 解密出明文 token（供 Spring AI 运行时使用）。
     * 仅限 Service / Engine 层内部调用，绝不暴露到 Controller。
     */
    public String decryptToken(Long id) {
        InsightAiModelConfig e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: id=" + id));
        if (e.getTokenEncrypted() == null || e.getTokenEncrypted().length == 0) {
            return null;
        }
        String encoded = new String(e.getTokenEncrypted(), StandardCharsets.UTF_8);
        return tokenEncryptor.decrypt(encoded);
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    /**
     * 仅当 req.token 非空时才覆盖加密字段；null/空白/****** 都视为"不更新"。
     * 这样前端回显时用 "******" 占位，回写更新不会误清空密钥。
     */
    private void applyTokenIfPresent(InsightAiModelConfig e, String token) {
        if (token == null || token.isBlank() || "******".equals(token)) {
            return;
        }
        String encrypted = tokenEncryptor.encrypt(token);
        e.setTokenEncrypted(encrypted.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 极简雪花 ID（项目内仅 4~8 行数据，单机够用）。
     * 41 位时间戳(毫秒) + 12 位序列 + 10 位机器/进程标识
     */
    private long generateSnowflakeId() {
        long ts = System.currentTimeMillis() - 1700000000000L; // 2023-11 起点
        long seq = (System.nanoTime() & 0xFFF);
        long node = (Runtime.getRuntime().hashCode() & 0x3FF);
        return (ts << 22) | (node << 12) | seq;
    }
}