package com.llm.insight.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Shared base for controller slice tests.
 *
 * <p>Spring Boot 4.x removed {@code @WebMvcTest} and {@code AutoConfigureMockMvc}.
 * This base wires a {@link MockMvc} via {@link MockMvcBuilders#standaloneSetup}
 * so we still exercise the real Spring MVC serialization layer
 * (Jackson, {@code @PathVariable}, {@code @RequestParam}, {@code @RestControllerAdvice},
 * UTF-8 JSON) without pulling in the full application context.
 *
 * <p>Subclasses use {@link org.springframework.test.context.bean.override.mockito.MockitoBean}
 * to declare service collaborators and override
 * {@link #controllerOrAdvice()} to provide the controller(s) under test plus any
 * {@code @RestControllerAdvice} beans that should participate.
 */
public abstract class BaseWebMvcTest {

    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper;

    protected abstract Object[] controllerOrAdvice();

    @BeforeEach
    void setUpBase() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(this.objectMapper);
        jacksonConverter.setSupportedMediaTypes(java.util.List.of(APPLICATION_JSON));

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controllerOrAdvice())
                .setControllerAdvice(getControllerAdvices())
                .setMessageConverters(
                        jacksonConverter,
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter())
                .build();
    }

    /**
     * Override to register custom {@code @RestControllerAdvice} beans
     * (e.g. {@code GlobalExceptionHandler}). Default: no extra advice.
     */
    protected Object[] getControllerAdvices() {
        return new Object[0];
    }
}
