package com.cos.orchestration.process;

import com.cos.domain.enums.ErrorSource;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationError;
import com.cos.domain.model.Party;
import com.cos.domain.model.ProductRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Process Workbook, "Validate Request Payload" tab: shallow, structural checks only - every
 * business rule is left to DRS. Note: {@code customerType}/{@code requestType} "not a valid
 * enum value" is not separately checked here, since Jackson already rejects an unrecognised enum
 * string before this service ever sees the request - only null/missing is reachable in Java.
 */
@Component
public class ValidateRequestPayloadService {

    public List<ApplicationError> validate(Application application) {
        List<ApplicationError> errors = new ArrayList<>();
        checkCoreFields(application, errors);
        if (errors.isEmpty()) {
            checkPartiesForCustomerType(application, errors);
        }
        if (errors.isEmpty()) {
            checkProductRequest(application, errors);
        }
        return errors;
    }

    private void checkCoreFields(Application application, List<ApplicationError> errors) {
        requireField(application.getApplicationId() != null, "applicationId", errors);
        requireField(application.getApplicationTimestamp() != null, "applicationTimestamp", errors);
        requireField(application.getChannel() != null, "channel", errors);
        requireField(application.getCountryCode() != null, "countryCode", errors);
        requireField(application.getRequestType() != null, "requestType", errors);
        requireField(application.getCustomerType() != null, "customerType", errors);
        requireField(application.getHasCreditApproval() != null, "hasCreditApproval", errors);
    }

    private void requireField(boolean present, String fieldName, List<ApplicationError> errors) {
        if (!present) {
            errors.add(validationError("VAL01", "Missing required field: " + fieldName));
        }
    }

    private void checkPartiesForCustomerType(Application application, List<ApplicationError> errors) {
        Party applicant = findByRole(application, PartyRole.Applicant);
        if (applicant == null) {
            errors.add(validationError("VAL02", "No party with partyRole=Applicant"));
            return;
        }
        if (application.getCustomerType() == PartyType.Consumer) {
            if (applicant.getPartyType() != PartyType.Consumer || applicant.getPerson() == null) {
                errors.add(validationError("VAL02",
                        "customerType=Consumer requires the Applicant to be a Consumer party with Person populated"));
            }
        } else if (application.getCustomerType() == PartyType.Business) {
            if (applicant.getPartyType() != PartyType.Business || applicant.getCompany() == null) {
                errors.add(validationError("VAL02",
                        "customerType=Business requires the Applicant to be a Business party with Company populated"));
            }
            if (findByRole(application, PartyRole.Representative) == null
                    && findByRole(application, PartyRole.Director) == null
                    && findByRole(application, PartyRole.BeneficialOwner) == null) {
                errors.add(validationError("VAL02",
                        "customerType=Business requires a linked Representative, Director or BeneficialOwner party"));
            }
        }
    }

    private void checkProductRequest(Application application, List<ApplicationError> errors) {
        List<ProductRequest> productRequests = application.getProductRequests();
        if (productRequests == null || productRequests.isEmpty()) {
            errors.add(validationError("VAL03", "productRequests[] is empty"));
            return;
        }
        for (ProductRequest productRequest : productRequests) {
            if (productRequest.getRequestedAmount() == null
                    || productRequest.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(validationError("VAL03", "requestedAmount must be greater than 0"));
            }
            if (productRequest.getProductCode() == null || productRequest.getProductCode().isBlank()) {
                errors.add(validationError("VAL03", "productCode is missing"));
            }
        }
    }

    private Party findByRole(Application application, PartyRole role) {
        if (application.getParties() == null) {
            return null;
        }
        return application.getParties().stream().filter(p -> p.getPartyRole() == role).findFirst().orElse(null);
    }

    private ApplicationError validationError(String code, String message) {
        return ApplicationError.builder()
                .code(code)
                .message(message)
                .source(ErrorSource.Validation)
                .occurredOn(LocalDateTime.now())
                .isResolved(Boolean.FALSE)
                .build();
    }
}
