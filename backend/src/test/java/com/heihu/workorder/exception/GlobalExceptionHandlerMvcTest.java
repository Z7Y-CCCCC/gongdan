package com.heihu.workorder.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.controller.AuthController;
import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerMvcTest {

    private final AuthService authService = mock(AuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Reuse a production controller; do not register additional component-scanned API routes.
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class)))
                .thenReturn(new AuthResponse("fixture-token", null, false));
    }

    @Test
    void unsupportedMethodReturns405WithAllowAndSafeJson() throws Exception {
        mvc.perform(get("/api/auth/login").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("不支持的请求方法"))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void unsupportedContentTypeReturns415WithSafeJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.APPLICATION_JSON).content("invalid-format"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("不支持的请求内容类型"));
        verifyNoInteractions(authService);
    }

    @Test
    void unsupportedAcceptReturns406AndStillProvidesTheJsonErrorContract() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN).content(validLoginJson()))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("无法提供请求的响应格式"));
    }

    @Test
    void malformedJsonRemains400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数不完整或格式不正确"));
        verifyNoInteractions(authService);
    }

    @Test
    void supportedJsonRequestIsUnchanged() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content(validLoginJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("fixture-token"));
    }

    private String validLoginJson() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setFactoryCode("FIXTURE");
        request.setUsername("fixture-user");
        request.setPassword("fixture-password");
        return new ObjectMapper().writeValueAsString(request);
    }
}
