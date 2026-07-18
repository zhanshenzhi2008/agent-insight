package com.llm.insight.explorer.controller;

import com.llm.insight.common.ApiResponse;
import com.llm.insight.dto.AiVendorRequest;
import com.llm.insight.dto.AiVendorResponse;
import com.llm.insight.explorer.service.InsightAiVendorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiVendorController}.
 * Covers: list / getById / getByVendor / create / update / delete.
 */
class AiVendorControllerTest {

    private AiVendorController controller(InsightAiVendorService service) {
        return new AiVendorController(service);
    }

    private static AiVendorResponse vendorResponse(Long id, String vendor) {
        AiVendorResponse r = new AiVendorResponse();
        r.setId(id);
        r.setVendor(vendor);
        r.setDisplayName(vendor);
        r.setStatus(1);
        r.setTimeoutSeconds(30);
        r.setMaxRetries(3);
        r.setToken("******");
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        return r;
    }

    // ------------------------------------------------------------------
    // TC-V-01~02: List
    // ------------------------------------------------------------------

    @Nested
    class ListVendors {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors returns all vendors")
        void listReturnsAll() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            when(svc.list()).thenReturn(List.of(
                    vendorResponse(1L, "openai"),
                    vendorResponse(2L, "anthropic")
            ));

            ApiResponse<List<AiVendorResponse>> resp = controller(svc).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).hasSize(2);
            assertThat(resp.getData().get(0).getVendor()).isEqualTo("openai");
            assertThat(resp.getData().get(1).getVendor()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors returns empty list when none")
        void listReturnsEmpty() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            when(svc.list()).thenReturn(List.of());

            ApiResponse<List<AiVendorResponse>> resp = controller(svc).list();

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // TC-V-03: GetById
    // ------------------------------------------------------------------

    @Nested
    class GetById {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors/{id} returns vendor when found")
        void getByIdFound() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorResponse r = vendorResponse(5L, "openai");
            when(svc.getById(5L)).thenReturn(Optional.of(r));

            ApiResponse<AiVendorResponse> resp = controller(svc).getById(5L);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getId()).isEqualTo(5L);
            assertThat(resp.getData().getToken()).isEqualTo("******"); // 脱敏
        }

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors/{id} returns error when not found")
        void getByIdNotFound() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            when(svc.getById(999L)).thenReturn(Optional.empty());

            ApiResponse<AiVendorResponse> resp = controller(svc).getById(999L);

            assertThat(resp.getCode()).isNotEqualTo(0);
            assertThat(resp.getMessage()).contains("不存在");
        }
    }

    // ------------------------------------------------------------------
    // TC-V-04: GetByVendor
    // ------------------------------------------------------------------

    @Nested
    class GetByVendor {

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors/by-vendor/{vendor} returns vendor")
        void getByVendorFound() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorResponse r = vendorResponse(1L, "openai");
            when(svc.getByVendor("openai")).thenReturn(Optional.of(r));

            ApiResponse<AiVendorResponse> resp = controller(svc).getByVendor("openai");

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getVendor()).isEqualTo("openai");
        }

        @Test
        @DisplayName("GET /api/v1/explorer/ai-vendors/by-vendor/{vendor} returns error when not found")
        void getByVendorNotFound() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            when(svc.getByVendor("nope")).thenReturn(Optional.empty());

            ApiResponse<AiVendorResponse> resp = controller(svc).getByVendor("nope");

            assertThat(resp.getCode()).isNotEqualTo(0);
            assertThat(resp.getMessage()).contains("不存在");
        }
    }

    // ------------------------------------------------------------------
    // TC-V-05: Create
    // ------------------------------------------------------------------

    @Nested
    class CreateVendor {

        @Test
        @DisplayName("POST /api/v1/explorer/ai-vendors creates vendor and returns response")
        void createSucceeds() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorRequest req = new AiVendorRequest();
            req.setVendor("ollama");
            req.setDisplayName("Ollama");
            req.setBaseUrl("http://localhost:11434");
            req.setStatus(1);
            req.setTimeoutSeconds(60);

            AiVendorResponse saved = new AiVendorResponse();
            saved.setId(7L);
            saved.setVendor("ollama");
            saved.setDisplayName("Ollama");
            saved.setBaseUrl("http://localhost:11434");
            saved.setStatus(1);
            saved.setTimeoutSeconds(60);
            saved.setToken(null); // 新建无 token 时 Service 返回 null
            when(svc.create(any(AiVendorRequest.class))).thenReturn(saved);

            ApiResponse<AiVendorResponse> resp = controller(svc).create(req);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getVendor()).isEqualTo("ollama");
            assertThat(resp.getData().getToken()).isNull();
            verify(svc).create(any(AiVendorRequest.class));
        }

        @Test
        @DisplayName("POST creates vendor with token — token field is masked in response")
        void createWithTokenIsMasked() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorRequest req = new AiVendorRequest();
            req.setVendor("openai");
            req.setStatus(1);
            req.setToken("sk-real-key");

            AiVendorResponse saved = vendorResponse(1L, "openai");
            saved.setToken("******");
            when(svc.create(any(AiVendorRequest.class))).thenReturn(saved);

            ApiResponse<AiVendorResponse> resp = controller(svc).create(req);

            // controller 层不处理 token，直接透传 Service 返回的响应
            assertThat(resp.getData().getToken()).isEqualTo("******");
        }
    }

    // ------------------------------------------------------------------
    // TC-V-06: Update
    // ------------------------------------------------------------------

    @Nested
    class UpdateVendor {

        @Test
        @DisplayName("PUT /api/v1/explorer/ai-vendors/{id} updates and returns updated vendor")
        void updateSucceeds() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorRequest req = new AiVendorRequest();
            req.setVendor("openai");
            req.setDisplayName("OpenAI Updated");
            req.setStatus(1);

            AiVendorResponse updated = vendorResponse(1L, "openai");
            updated.setDisplayName("OpenAI Updated");
            when(svc.update(eq(1L), any(AiVendorRequest.class))).thenReturn(updated);

            ApiResponse<AiVendorResponse> resp = controller(svc).update(1L, req);

            assertThat(resp.getCode()).isEqualTo(0);
            assertThat(resp.getData().getDisplayName()).isEqualTo("OpenAI Updated");
            verify(svc).update(eq(1L), any(AiVendorRequest.class));
        }

        @Test
        @DisplayName("PUT with new token updates the token")
        void updateWithNewToken() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            AiVendorRequest req = new AiVendorRequest();
            req.setVendor("openai");
            req.setStatus(1);
            req.setToken("sk-new-key");

            AiVendorResponse updated = vendorResponse(1L, "openai");
            updated.setToken("******");
            when(svc.update(eq(1L), any(AiVendorRequest.class))).thenReturn(updated);

            ApiResponse<AiVendorResponse> resp = controller(svc).update(1L, req);

            assertThat(resp.getData().getToken()).isEqualTo("******");
        }
    }

    // ------------------------------------------------------------------
    // TC-V-07: Delete
    // ------------------------------------------------------------------

    @Nested
    class DeleteVendor {

        @Test
        @DisplayName("DELETE /api/v1/explorer/ai-vendors/{id} returns ok")
        void deleteSucceeds() {
            InsightAiVendorService svc = mock(InsightAiVendorService.class);
            doNothing().when(svc).delete(3L);

            ApiResponse<Void> resp = controller(svc).delete(3L);

            assertThat(resp.getCode()).isEqualTo(0);
            verify(svc).delete(3L);
        }
    }
}
