package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.AiModelInstanceRequest;
import com.llm.insight.dto.AiModelInstanceResponse;
import com.llm.insight.explorer.service.InsightModelInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiModelInstanceController}.
 * Covers: list / getById / listByVendor / listByCapability / create / update / delete.
 */
class AiModelInstanceControllerTest {

    private AiModelInstanceController controller(InsightModelInstanceService service) {
        return new AiModelInstanceController(service);
    }

    private static AiModelInstanceResponse instanceResponse(Long id, Long vendorId, String vendor) {
        AiModelInstanceResponse r = new AiModelInstanceResponse();
        r.setId(id);
        r.setVendorId(vendorId);
        r.setVendor(vendor);
        r.setModelName("gpt-4o");
        r.setCapability("CHAT");
        r.setTier("PRODUCTION");
        r.setPriority(1);
        r.setMaxTokens(8192);
        r.setTemperature(new BigDecimal("0.7"));
        r.setTopP(new BigDecimal("1.0"));
        r.setIsActive(1);
        r.setIsCurrent(1);
        r.setDescription("测试实例");
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        return r;
    }

    // ------------------------------------------------------------------
    // TC-MI-01: List all
    // ------------------------------------------------------------------

    @Nested
    class ListAll {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-model-instances returns all instances")
        void listAllReturnsAll() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            when(svc.list()).thenReturn(List.of(
                    instanceResponse(1L, 1L, "openai"),
                    instanceResponse(2L, 2L, "anthropic"),
                    instanceResponse(3L, 3L, "ollama")
            ));

            ApiResponse<List<AiModelInstanceResponse>> resp = controller(svc).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(3);
            assertThat(resp.getData().get(0).getModelName()).isEqualTo("gpt-4o");
            assertThat(resp.getData().get(0).getVendor()).isEqualTo("openai");
        }

        @Test
        @DisplayName("GET returns empty list when no instances")
        void listReturnsEmpty() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            when(svc.list()).thenReturn(List.of());

            ApiResponse<List<AiModelInstanceResponse>> resp = controller(svc).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-02: GetById
    // ------------------------------------------------------------------

    @Nested
    class GetById {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-model-instances/{id} returns instance")
        void getByIdFound() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            AiModelInstanceResponse r = instanceResponse(5L, 1L, "openai");
            when(svc.getById(5L)).thenReturn(Optional.of(r));

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).getById(5L);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getId()).isEqualTo(5L);
            assertThat(resp.getData().getIsCurrent()).isEqualTo(1);
        }

        @Test
        @DisplayName("GET /api/v1/explorer/ai-model-instances/{id} returns error when not found")
        void getByIdNotFound() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            when(svc.getById(999L)).thenReturn(Optional.empty());

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).getById(999L);

            assertThat(resp.getCode()).isNotEqualTo(0);
            assertThat(resp.getMessage()).contains("不存在");
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-03: ListByVendor
    // ------------------------------------------------------------------

    @Nested
    class ListByVendor {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-model-instances/by-vendor/{vendorId} returns instances")
        void listByVendorReturns() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            when(svc.listByVendor(3L)).thenReturn(List.of(
                    instanceResponse(10L, 3L, "ollama"),
                    instanceResponse(11L, 3L, "ollama")
            ));

            ApiResponse<List<AiModelInstanceResponse>> resp = controller(svc).listByVendor(3L);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getVendor()).isEqualTo("ollama");
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-04: ListByCapability
    // ------------------------------------------------------------------

    @Nested
    class ListByCapability {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-model-instances/by-capability/{capability} returns instances")
        void listByCapabilityReturns() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            when(svc.listByCapability("EMBEDDING")).thenReturn(List.of(
                    instanceResponse(20L, 1L, "openai")
            ));

            ApiResponse<List<AiModelInstanceResponse>> resp = controller(svc).listByCapability("EMBEDDING");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getVendor()).isEqualTo("openai");
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-05: Create
    // ------------------------------------------------------------------

    @Nested
    class CreateInstance {

        @Test
        @DisplayName("POST creates instance with all fields")
        void createSucceeds() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            AiModelInstanceRequest req = new AiModelInstanceRequest();
            req.setVendorId(1L);
            req.setModelName("gpt-4o");
            req.setCapability("CHAT");
            req.setTier("PRODUCTION");
            req.setTemperature(new BigDecimal("0.7"));
            req.setMaxTokens(8192);
            req.setIsActive(1);
            req.setIsCurrent(1);

            AiModelInstanceResponse saved = instanceResponse(15L, 1L, "openai");
            when(svc.create(any(AiModelInstanceRequest.class))).thenReturn(saved);

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).create(req);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getModelName()).isEqualTo("gpt-4o");
            assertThat(resp.getData().getIsCurrent()).isEqualTo(1);
            assertThat(resp.getData().getTemperature()).isEqualByComparingTo(new BigDecimal("0.7"));
            verify(svc).create(any(AiModelInstanceRequest.class));
        }

        @Test
        @DisplayName("POST creates embedding instance with defaults")
        void createEmbeddingWithDefaults() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            AiModelInstanceRequest req = new AiModelInstanceRequest();
            req.setVendorId(2L);
            req.setModelName("bge-m3");
            req.setCapability("EMBEDDING");
            req.setTier("PRODUCTION");

            AiModelInstanceResponse saved = instanceResponse(20L, 2L, "ollama");
            saved.setCapability("EMBEDDING");
            when(svc.create(any(AiModelInstanceRequest.class))).thenReturn(saved);

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).create(req);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getCapability()).isEqualTo("EMBEDDING");
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-06: Update
    // ------------------------------------------------------------------

    @Nested
    class UpdateInstance {

        @Test
        @DisplayName("PUT updates temperature and maxTokens")
        void updateSucceeds() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            AiModelInstanceRequest req = new AiModelInstanceRequest();
            req.setVendorId(1L);
            req.setModelName("gpt-4o");
            req.setCapability("CHAT");
            req.setTier("PRODUCTION");
            req.setTemperature(new BigDecimal("0.3"));
            req.setMaxTokens(4096);

            AiModelInstanceResponse updated = instanceResponse(1L, 1L, "openai");
            updated.setTemperature(new BigDecimal("0.3"));
            updated.setMaxTokens(4096);
            when(svc.update(eq(1L), any(AiModelInstanceRequest.class))).thenReturn(updated);

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).update(1L, req);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getTemperature()).isEqualByComparingTo(new BigDecimal("0.3"));
            assertThat(resp.getData().getMaxTokens()).isEqualTo(4096);
            verify(svc).update(eq(1L), any(AiModelInstanceRequest.class));
        }

        @Test
        @DisplayName("PUT switches isCurrent to another instance")
        void updateSwitchesCurrent() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            AiModelInstanceRequest req = new AiModelInstanceRequest();
            req.setVendorId(1L);
            req.setModelName("gpt-4o-mini");
            req.setCapability("CHAT");
            req.setTier("PRODUCTION");
            req.setIsCurrent(1);

            AiModelInstanceResponse updated = instanceResponse(2L, 1L, "openai");
            updated.setIsCurrent(1);
            when(svc.update(eq(2L), any(AiModelInstanceRequest.class))).thenReturn(updated);

            ApiResponse<AiModelInstanceResponse> resp = controller(svc).update(2L, req);

            assertThat(resp.getData().getIsCurrent()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // TC-MI-07: Delete
    // ------------------------------------------------------------------

    @Nested
    class DeleteInstance {

        @Test
        @DisplayName("DELETE returns ok")
        void deleteSucceeds() {
            InsightModelInstanceService svc = mock(InsightModelInstanceService.class);
            doNothing().when(svc).delete(7L);

            ApiResponse<Void> resp = controller(svc).delete(7L);

            assertThat(resp.getCode()).isEqualTo(0);
            verify(svc).delete(7L);
        }
    }
}
