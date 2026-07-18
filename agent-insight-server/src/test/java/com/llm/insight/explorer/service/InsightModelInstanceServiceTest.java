package com.llm.insight.explorer.service;

import com.llm.insight.dto.AiModelInstanceRequest;
import com.llm.insight.dto.AiModelInstanceResponse;
import com.llm.insight.repository.InsightAiVendorRepository;
import com.llm.insight.repository.InsightModelInstanceRepository;
import com.llm.insight.repository.entity.InsightAiVendor;
import com.llm.insight.repository.entity.InsightModelInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightModelInstanceServiceTest {

    private InsightModelInstanceRepository instRepo;
    private InsightAiVendorRepository vendorRepo;
    private InsightModelInstanceService service;

    @BeforeEach
    void setUp() {
        instRepo = mock(InsightModelInstanceRepository.class);
        vendorRepo = mock(InsightAiVendorRepository.class);
        service = new InsightModelInstanceService(instRepo, vendorRepo);
    }

    private InsightAiVendor vendor(long id, String name) {
        InsightAiVendor v = new InsightAiVendor();
        v.setId(id);
        v.setVendor(name);
        return v;
    }

    private InsightModelInstance inst(long id, long vendorId, String cap, String tier, int isCurrent) {
        InsightModelInstance i = new InsightModelInstance();
        i.setId(id);
        i.setVendorId(vendorId);
        i.setModelName("m-" + id);
        i.setCapability(cap);
        i.setTier(tier);
        i.setPriority(1);
        i.setIsActive(1);
        i.setIsCurrent(isCurrent);
        return i;
    }

    @Test
    @DisplayName("create sets defaults and saves entity")
    void createPersistsEntity() {
        when(vendorRepo.existsById(1L)).thenReturn(true);
        when(instRepo.save(any(InsightModelInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelInstanceRequest req = new AiModelInstanceRequest();
        req.setVendorId(1L);
        req.setModelName("gpt-4o");
        req.setCapability("CHAT");
        req.setTier("PRODUCTION");
        req.setTemperature(new BigDecimal("0.7"));

        AiModelInstanceResponse out = service.create(req);
        assertThat(out.getModelName()).isEqualTo("gpt-4o");
        assertThat(out.getCapability()).isEqualTo("CHAT");
        assertThat(out.getTier()).isEqualTo("PRODUCTION");
        assertThat(out.getTemperature()).isEqualByComparingTo(new BigDecimal("0.7"));
    }

    @Test
    @DisplayName("create rejects unknown vendorId")
    void createRejectsUnknownVendor() {
        when(vendorRepo.existsById(99L)).thenReturn(false);
        AiModelInstanceRequest req = new AiModelInstanceRequest();
        req.setVendorId(99L);
        req.setModelName("x");
        req.setCapability("CHAT");
        req.setTier("LIGHT");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vendor 不存在");
    }

    @Test
    @DisplayName("create rejects blank capability/tier")
    void createRejectsBlankCapability() {
        when(vendorRepo.existsById(1L)).thenReturn(true);
        AiModelInstanceRequest req = new AiModelInstanceRequest();
        req.setVendorId(1L);
        req.setModelName("x");
        req.setCapability("");
        req.setTier("LIGHT");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capability");
    }

    @Test
    @DisplayName("resolveCurrent prefers is_current=1, falls back to priority")
    void resolveCurrentPrefersCurrentFlag() {
        InsightModelInstance a = inst(1L, 1L, "CHAT", "PRODUCTION", 0);
        InsightModelInstance b = inst(2L, 2L, "CHAT", "PRODUCTION", 1);

        when(instRepo.findFirstByCapabilityAndTierAndIsCurrentOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(Optional.of(b));
        Optional<InsightModelInstance> r = service.resolveCurrent("CHAT", "PRODUCTION");
        assertThat(r).isPresent();
        assertThat(r.get().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("resolveCurrent falls back to active when no current flag set")
    void resolveCurrentFallbackToActive() {
        InsightModelInstance a = inst(1L, 1L, "CHAT", "PRODUCTION", 0);
        when(instRepo.findFirstByCapabilityAndTierAndIsCurrentOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(Optional.empty());
        when(instRepo.findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(List.of(a));
        Optional<InsightModelInstance> r = service.resolveCurrent("CHAT", "PRODUCTION");
        assertThat(r).isPresent();
        assertThat(r.get().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("resolveCurrent returns empty when nothing active")
    void resolveCurrentEmpty() {
        when(instRepo.findFirstByCapabilityAndTierAndIsCurrentOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(Optional.empty());
        when(instRepo.findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(List.of());
        assertThat(service.resolveCurrent("CHAT", "PRODUCTION")).isEmpty();
    }

    @Test
    @DisplayName("setting isCurrent=1 demotes other current instances in same (capability, tier)")
    void createDemotesOtherCurrent() {
        when(vendorRepo.existsById(1L)).thenReturn(true);
        InsightModelInstance other = inst(99L, 1L, "CHAT", "PRODUCTION", 1);
        when(instRepo.findByCapabilityAndTierAndIsActiveOrderByPriorityAsc(
                eq("CHAT"), eq("PRODUCTION"), eq(1)))
                .thenReturn(List.of(other));
        when(instRepo.save(any(InsightModelInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelInstanceRequest req = new AiModelInstanceRequest();
        req.setVendorId(1L);
        req.setModelName("gpt-4o-mini");
        req.setCapability("CHAT");
        req.setTier("PRODUCTION");
        req.setIsCurrent(1);

        service.create(req);

        ArgumentCaptor<InsightModelInstance> captor = ArgumentCaptor.forClass(InsightModelInstance.class);
        // 应该至少保存两次（demote 老的 + save 新的）
        org.mockito.Mockito.verify(instRepo, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        boolean oldDemoted = captor.getAllValues().stream()
                .anyMatch(i -> i.getId().equals(99L) && i.getIsCurrent() == 0);
        assertThat(oldDemoted).isTrue();
    }

    @Test
    @DisplayName("listByVendor returns instances with vendor name populated")
    void listByVendorNames() {
        InsightModelInstance i = inst(1L, 7L, "CHAT", "PRODUCTION", 1);
        when(instRepo.findByVendorIdOrderByIdAsc(7L)).thenReturn(List.of(i));
        when(vendorRepo.findAllById(any())).thenReturn(List.of(vendor(7L, "openai")));

        List<AiModelInstanceResponse> out = service.listByVendor(7L);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getVendor()).isEqualTo("openai");
    }
}