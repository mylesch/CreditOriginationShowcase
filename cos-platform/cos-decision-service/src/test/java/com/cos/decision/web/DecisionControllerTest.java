package com.cos.decision.web;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Party;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cos.chaos.enabled=true")
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evaluateReturnsPartyDecisions() throws Exception {
        Application application = Application.builder()
                .applicationId("APP-1")
                .parties(List.of(Party.builder()
                        .partyId("P1")
                        .partyType(PartyType.Consumer)
                        .bureauResponses(List.of(BureauResponse.builder()
                                .bureauResponseId("BR-1")
                                .checkType(BureauCheckType.Compliance)
                                .isPEPsMatch(Boolean.FALSE)
                                .build()))
                        .build()))
                .build();

        mockMvc.perform(post("/v1/applications/evaluate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parties[0].decisions.customerPEPsStatus").value("APPROVE"));
    }

    @Test
    void chaosHeaderInjectsRequestedHttpStatus() throws Exception {
        Application application = Application.builder().applicationId("APP-1").build();

        mockMvc.perform(post("/v1/applications/evaluate")
                        .contentType(APPLICATION_JSON)
                        .header("X-Chaos-Inject", "503")
                        .content(objectMapper.writeValueAsString(application)))
                .andExpect(status().isServiceUnavailable());
    }
}
