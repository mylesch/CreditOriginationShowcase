package com.cos.orchestration.web;

import com.cos.domain.enums.ApplicationStatus;
import com.cos.domain.model.Application;
import com.cos.orchestration.process.CreditOriginationOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrchestrationControllerTest {

    @Mock
    private CreditOriginationOrchestrator orchestrator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void originateDelegatesToOrchestratorAndReturnsItsResult() throws Exception {
        when(orchestrator.originate(any())).thenReturn(
                Application.builder().applicationId("APP-1").status(ApplicationStatus.REJECTED).build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrchestrationController(orchestrator)).build();

        mockMvc.perform(post("/v1/applications/originate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Application.builder().applicationId("APP-1").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
