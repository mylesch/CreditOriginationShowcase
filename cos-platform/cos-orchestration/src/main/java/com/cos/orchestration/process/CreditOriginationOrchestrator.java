package com.cos.orchestration.process;

import com.cos.domain.enums.ApplicationRouting;
import com.cos.domain.enums.ApplicationStatus;
import com.cos.domain.enums.ErrorSource;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationError;
import com.cos.orchestration.client.ApplicationErrors;
import com.cos.orchestration.client.DrsClient;
import com.cos.orchestration.client.IntegrationCallException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Process Workbook, "Credit Origination Orchestration" tab - the only process triggered directly
 * by an inbound request. Validates, calls DRS, and - while DRS reports
 * {@code EXTERNAL_DATA_REQUIRED} - gathers bureau data and re-invokes DRS, up to
 * {@code maxExternalDataCycles}.
 *
 * <p>Deliberately does not set {@code Application.status} for a normal decision outcome
 * (CREDIT_APPROVE, CREDIT_DECLINE, etc.) - the workbook's "Return Decision Response" step only
 * maps {@code Application.decisions}/{@code Party.decisions} onto the response for that path and
 * never assigns a status; status is only set here for REJECTED, EXCEPTION and
 * EXCEPTION_MANUAL_REVIEW, exactly as documented.
 */
@Service
public class CreditOriginationOrchestrator {

    private final ValidateRequestPayloadService validateRequestPayloadService;
    private final DrsClient drsClient;
    private final GatherExternalDataService gatherExternalDataService;
    private final int maxExternalDataCycles;

    public CreditOriginationOrchestrator(ValidateRequestPayloadService validateRequestPayloadService,
                                           DrsClient drsClient,
                                           GatherExternalDataService gatherExternalDataService,
                                           @Value("${cos.orchestration.max-external-data-cycles:3}") int maxExternalDataCycles) {
        this.validateRequestPayloadService = validateRequestPayloadService;
        this.drsClient = drsClient;
        this.gatherExternalDataService = gatherExternalDataService;
        this.maxExternalDataCycles = maxExternalDataCycles;
    }

    public Application originate(Application application) {
        application.setSubmittedOn(LocalDateTime.now());

        List<ApplicationError> validationErrors = validateRequestPayloadService.validate(application);
        if (!validationErrors.isEmpty()) {
            application.setErrors(validationErrors);
            application.setStatus(ApplicationStatus.REJECTED);
            return application;
        }

        application.setDrsCycleCount(0);
        while (true) {
            application.setDrsCycleCount(application.getDrsCycleCount() + 1);

            Application afterDrs;
            try {
                afterDrs = drsClient.evaluate(application);
            } catch (IntegrationCallException e) {
                ApplicationErrors.append(application, ApplicationErrors.fromIntegrationFailure(e));
                application.setStatus(ApplicationStatus.EXCEPTION);
                return application;
            }
            application = afterDrs;

            ApplicationRouting routing = application.getDecisions() == null
                    ? null : application.getDecisions().getApplicationRouting();
            if (routing != ApplicationRouting.EXTERNAL_DATA_REQUIRED) {
                return application;
            }

            if (application.getDrsCycleCount() >= maxExternalDataCycles) {
                application.setStatus(ApplicationStatus.EXCEPTION_MANUAL_REVIEW);
                ApplicationErrors.append(application, cycleLimitExceededError());
                return application;
            }

            gatherExternalDataService.gather(application);
        }
    }

    private ApplicationError cycleLimitExceededError() {
        return ApplicationError.builder()
                .code("ORCH01")
                .message("Cycle-limit guard reached maxExternalDataCycles=" + maxExternalDataCycles
                        + " while applicationRouting was still EXTERNAL_DATA_REQUIRED")
                .source(ErrorSource.System)
                .occurredOn(LocalDateTime.now())
                .isResolved(Boolean.FALSE)
                .build();
    }
}
