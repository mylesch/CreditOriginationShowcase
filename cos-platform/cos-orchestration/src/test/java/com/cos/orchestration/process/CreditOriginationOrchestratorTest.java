package com.cos.orchestration.process;

import com.cos.domain.enums.ApplicationRouting;
import com.cos.domain.enums.ApplicationStatus;
import com.cos.domain.enums.ErrorSource;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationDecisions;
import com.cos.domain.model.ApplicationError;
import com.cos.orchestration.client.DrsClient;
import com.cos.orchestration.client.IntegrationCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditOriginationOrchestratorTest {

    private ValidateRequestPayloadService validateRequestPayloadService;
    private DrsClient drsClient;
    private GatherExternalDataService gatherExternalDataService;

    @BeforeEach
    void setUp() {
        validateRequestPayloadService = mock(ValidateRequestPayloadService.class);
        drsClient = mock(DrsClient.class);
        gatherExternalDataService = mock(GatherExternalDataService.class);
        when(validateRequestPayloadService.validate(any())).thenReturn(List.of());
    }

    private CreditOriginationOrchestrator orchestrator(int maxExternalDataCycles) {
        return new CreditOriginationOrchestrator(validateRequestPayloadService, drsClient, gatherExternalDataService,
                maxExternalDataCycles);
    }

    @Test
    void validationFailureRejectsWithoutCallingDrsOrBureaus() {
        when(validateRequestPayloadService.validate(any())).thenReturn(
                List.of(ApplicationError.builder().code("VAL01").source(ErrorSource.Validation).build()));

        Application result = orchestrator(3).originate(Application.builder().build());

        assertEquals(ApplicationStatus.REJECTED, result.getStatus());
        verify(drsClient, never()).evaluate(any());
        verify(gatherExternalDataService, never()).gather(any());
    }

    @Test
    void drsCallFailureEndsInException() {
        when(drsClient.evaluate(any())).thenThrow(
                new IntegrationCallException("Decision Rules Service", "INT-DRS-01", "boom", 2));

        Application result = orchestrator(3).originate(Application.builder().build());

        assertEquals(ApplicationStatus.EXCEPTION, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals("INT-DRS-01", result.getErrors().get(0).getCode());
    }

    @Test
    void straightThroughDecisionReturnsWithoutGatheringExternalData() {
        Application afterDrs = Application.builder()
                .decisions(ApplicationDecisions.builder().applicationRouting(ApplicationRouting.CREDIT_APPROVE).build())
                .build();
        when(drsClient.evaluate(any())).thenReturn(afterDrs);

        Application result = orchestrator(3).originate(Application.builder().build());

        assertEquals(ApplicationRouting.CREDIT_APPROVE, result.getDecisions().getApplicationRouting());
        verify(gatherExternalDataService, never()).gather(any());
        verify(drsClient, times(1)).evaluate(any());
    }

    @Test
    void externalDataRequiredLoopsThenResolves() {
        Application cycle1 = Application.builder().drsCycleCount(1)
                .decisions(ApplicationDecisions.builder()
                        .applicationRouting(ApplicationRouting.EXTERNAL_DATA_REQUIRED).build())
                .build();
        Application cycle2 = Application.builder().drsCycleCount(2)
                .decisions(ApplicationDecisions.builder().applicationRouting(ApplicationRouting.CREDIT_APPROVE).build())
                .build();
        when(drsClient.evaluate(any())).thenReturn(cycle1).thenReturn(cycle2);

        Application result = orchestrator(3).originate(Application.builder().build());

        assertEquals(ApplicationRouting.CREDIT_APPROVE, result.getDecisions().getApplicationRouting());
        verify(gatherExternalDataService, times(1)).gather(any());
        verify(drsClient, times(2)).evaluate(any());
    }

    @Test
    void cycleLimitExceededEndsInExceptionManualReview() {
        // The real DRS echoes back whatever Application (including drsCycleCount) it was given,
        // as a freshly deserialized object carrying the same field values - simulate that round
        // trip rather than a single shared, repeatedly-mutated test double.
        when(drsClient.evaluate(any())).thenAnswer(invocation -> {
            Application request = invocation.getArgument(0);
            return Application.builder().drsCycleCount(request.getDrsCycleCount())
                    .decisions(ApplicationDecisions.builder()
                            .applicationRouting(ApplicationRouting.EXTERNAL_DATA_REQUIRED).build())
                    .build();
        });

        Application result = orchestrator(2).originate(Application.builder().build());

        assertEquals(ApplicationStatus.EXCEPTION_MANUAL_REVIEW, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ORCH01".equals(e.getCode())));
        verify(drsClient, times(2)).evaluate(any());
        verify(gatherExternalDataService, times(1)).gather(any());
    }
}
