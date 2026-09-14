package com.heihu.workorder.externaldata;
import com.heihu.workorder.service.*;
import com.heihu.workorder.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ExternalSourceControllerTest {
    MockMvc mvc; TokenService tokens; ExternalSourceService service;
    @BeforeEach void setup() {
        tokens=mock(TokenService.class); service=mock(ExternalSourceService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ExternalSourceController(tokens,service))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new SourceRequestLimitFilter()).build();
    }
    @Test void allReadAndActionEndpointsRequireAdmin() throws Exception {
        when(tokens.requireAdmin(any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN,"仅限管理员"));
        for(String path:new String[]{"","/capabilities","/1","/1/checks","/1/schema"})
            mvc.perform(get("/api/external-data-sources"+path).header("Authorization","Bearer production")).andExpect(status().isForbidden());
        mvc.perform(post("/api/external-data-sources/1/test").header("Authorization","Bearer production")).andExpect(status().isForbidden());
        for(String action:new String[]{"enable","disable","archive","restore"})
            mvc.perform(post("/api/external-data-sources/1/"+action).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        String body="{\"code\":\"x\",\"name\":\"x\"}";
        mvc.perform(post("/api/external-data-sources").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/external-data-sources/1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/external-data-sources/test-config").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void limitsBodyAndRedactsMalformedCredentialErrors() throws Exception {
        mvc.perform(post("/api/external-data-sources").contentType(MediaType.APPLICATION_JSON).content(new byte[16385])).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/external-data-sources").contentType(MediaType.APPLICATION_JSON).content("{\"secretValue\":\"private-secret\",oops}"))
            .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret"))));
        verifyNoInteractions(service);
    }
    @Test void requestLimitAlsoAppliesUnderANonRootServletContext() throws Exception {
        mvc.perform(post("/plant/api/external-data-sources").contextPath("/plant")
            .contentType(MediaType.APPLICATION_JSON).content(new byte[16385])).andExpect(status().isPayloadTooLarge());
        mvc.perform(put("/plant/api/external-data-sources/1").contextPath("/plant")
            .contentType(MediaType.APPLICATION_JSON).content(new byte[16385])).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }
}
