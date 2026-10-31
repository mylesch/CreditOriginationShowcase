package com.cos.orchestration.process;

import com.cos.domain.enums.Channel;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.RequestType;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationError;
import com.cos.domain.model.Company;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.domain.model.ProductRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateRequestPayloadServiceTest {

    private final ValidateRequestPayloadService service = new ValidateRequestPayloadService();

    @Test
    void completeConsumerApplicationPassesValidation() {
        Application application = validConsumerApplication();

        assertTrue(service.validate(application).isEmpty());
    }

    @Test
    void missingCoreFieldsRaiseVal01PerField() {
        Application application = Application.builder().build();

        List<ApplicationError> errors = service.validate(application);

        assertTrue(errors.size() >= 7);
        assertTrue(errors.stream().allMatch(e -> "VAL01".equals(e.getCode())));
    }

    @Test
    void consumerApplicantWithoutPersonRaisesVal02() {
        Application application = validConsumerApplication();
        application.getParties().get(0).setPerson(null);

        List<ApplicationError> errors = service.validate(application);

        assertEquals(1, errors.size());
        assertEquals("VAL02", errors.get(0).getCode());
    }

    @Test
    void businessApplicationWithoutLinkedRepresentativeRaisesVal02() {
        Application application = validBusinessApplicationWithoutRepresentative();

        List<ApplicationError> errors = service.validate(application);

        assertEquals(1, errors.size());
        assertEquals("VAL02", errors.get(0).getCode());
    }

    @Test
    void emptyProductRequestsRaisesVal03() {
        Application application = validConsumerApplication();
        application.setProductRequests(List.of());

        List<ApplicationError> errors = service.validate(application);

        assertEquals(1, errors.size());
        assertEquals("VAL03", errors.get(0).getCode());
    }

    @Test
    void nonPositiveRequestedAmountRaisesVal03() {
        Application application = validConsumerApplication();
        application.getProductRequests().get(0).setRequestedAmount(BigDecimal.ZERO);

        List<ApplicationError> errors = service.validate(application);

        assertEquals(1, errors.size());
        assertEquals("VAL03", errors.get(0).getCode());
    }

    private Application validConsumerApplication() {
        Party applicant = Party.builder()
                .partyId("P1")
                .partyRole(PartyRole.Applicant)
                .partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe").build())
                .build();
        return Application.builder()
                .applicationId("APP-1")
                .applicationTimestamp(LocalDateTime.now())
                .channel(Channel.Online)
                .countryCode("GB")
                .requestType(RequestType.FULL_ORIGINATION)
                .customerType(PartyType.Consumer)
                .hasCreditApproval(Boolean.FALSE)
                .parties(List.of(applicant))
                .productRequests(List.of(ProductRequest.builder()
                        .productRequestId("PR1").productCode("LOAN").requestedAmount(BigDecimal.TEN).build()))
                .build();
    }

    private Application validBusinessApplicationWithoutRepresentative() {
        Party applicant = Party.builder()
                .partyId("P1")
                .partyRole(PartyRole.Applicant)
                .partyType(PartyType.Business)
                .company(Company.builder().legalName("Example Ltd").build())
                .build();
        return Application.builder()
                .applicationId("APP-1")
                .applicationTimestamp(LocalDateTime.now())
                .channel(Channel.Online)
                .countryCode("GB")
                .requestType(RequestType.FULL_ORIGINATION)
                .customerType(PartyType.Business)
                .hasCreditApproval(Boolean.FALSE)
                .parties(List.of(applicant))
                .productRequests(List.of(ProductRequest.builder()
                        .productRequestId("PR1").productCode("LOAN").requestedAmount(BigDecimal.TEN).build()))
                .build();
    }
}
