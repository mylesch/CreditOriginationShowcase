package com.cos.domain;

import com.cos.domain.enums.*;
import com.cos.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Builds one Application that touches every entity in the domain model at least once, then
 * round-trips it through Jackson (serialize -> JSON -> deserialize) and asserts the result is
 * field-for-field identical to the original. This is the Phase 1 verification criterion from
 * the implementation plan: the Java model must round-trip the full BOM v0.5 shape without loss.
 */
class ApplicationRoundTripTest {

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void fullApplicationRoundTripsThroughJackson() throws Exception {
        Application application = buildSampleApplication();

        ObjectMapper mapper = mapper();
        String json = mapper.writeValueAsString(application);
        assertNotNull(json);

        Application roundTripped = mapper.readValue(json, Application.class);
        assertEquals(application, roundTripped);
    }

    @Test
    void documentExtractionRoundTripsThroughJackson() throws Exception {
        // DocumentExtraction is correlated to SubmittedDocument only by documentId - it is not
        // nested anywhere in the Application tree, so it gets its own round-trip test.
        DocumentExtraction extraction = DocumentExtraction.builder()
                .documentId("DOC-1")
                .extractedDocumentType(DocumentTypeCode.BankStatement)
                .extractionConfidence(new BigDecimal("0.97"))
                .extractedIdNumber("8001015009087")
                .extractedFullName("Applicant One")
                .extractedIncomeAmount(new BigDecimal("24500.00"))
                .extractedIncomeDate(LocalDate.of(2026, 9, 30))
                .extractedAddress("1 Example Street, Sampleton")
                .isNameMatch(Boolean.TRUE)
                .isIdentityMatch(Boolean.TRUE)
                .processedOn(LocalDateTime.of(2026, 10, 15, 9, 31))
                .isSuccessful(Boolean.TRUE)
                .build();

        ObjectMapper mapper = mapper();
        String json = mapper.writeValueAsString(extraction);
        DocumentExtraction roundTripped = mapper.readValue(json, DocumentExtraction.class);
        assertEquals(extraction, roundTripped);
    }

    private Application buildSampleApplication() {
        PersonReference director = PersonReference.builder()
                .fullName("Jane Director")
                .dateOfBirth(LocalDate.of(1975, 4, 12))
                .identificationNumber("ID-DIRECTOR-1")
                .role(PersonReferenceRole.Director)
                .build();

        Offer approvedOffer = Offer.builder()
                .offerId("OFFER-1")
                .type(OfferType.APPROVED)
                .amount(new BigDecimal("15000.00"))
                .creditLimit(new BigDecimal("15000.00"))
                .creditAllocation(new BigDecimal("12000.00"))
                .termMonths(36)
                .depositAmount(new BigDecimal("1000.00"))
                .depositPct(new BigDecimal("0.10"))
                .isApprovalRequired(Boolean.FALSE)
                .isPOIRequired(Boolean.TRUE)
                .isDebitOrderRequired(Boolean.TRUE)
                .build();

        ProductRequest productRequest = ProductRequest.builder()
                .productRequestId("PR-1")
                .productCode("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("15000.00"))
                .termMonths(36)
                .approvedOffer(approvedOffer)
                .selectedOffer(approvedOffer)
                .build();

        Address address = Address.builder()
                .addressId("ADDR-1")
                .addressType(AddressType.Residential)
                .line1("1 Example Street")
                .line2("Apt 4")
                .city("Sampleton")
                .state("Sample State")
                .postalCode("1234")
                .country("ZA")
                .startOn(LocalDate.of(2018, 1, 1))
                .yearsAtAddress(new BigDecimal("7.5"))
                .build();

        Phone phone = Phone.builder()
                .phoneId("PHONE-1")
                .phoneType(PhoneType.Mobile)
                .countryCode("+27")
                .number("821234567")
                .isPrimary(Boolean.TRUE)
                .build();

        Email email = Email.builder()
                .emailId("EMAIL-1")
                .emailType(EmailType.Personal)
                .email("applicant@example.com")
                .isPrimary(Boolean.TRUE)
                .build();

        Identification identification = Identification.builder()
                .identificationId("ID-1")
                .identificationType(IdentificationType.NationalID)
                .reference("8001015009087")
                .issuedOn(LocalDate.of(2010, 1, 1))
                .expiresOn(LocalDate.of(2030, 1, 1))
                .placeIssued("ZA")
                .isVerified(Boolean.TRUE)
                .build();

        BankAccount bankAccount = BankAccount.builder()
                .bankAccountId("BANK-1")
                .accountType(BankAccountType.Current)
                .accountNumber("1234567890")
                .bankName("Example Bank")
                .branchCode("000001")
                .accountHolderName("Applicant One")
                .yearsWithBank(new BigDecimal("4.0"))
                .isPrimary(Boolean.TRUE)
                .build();

        Employment employment = Employment.builder()
                .employmentId("EMP-1")
                .employerName("Example Corp")
                .employmentType(EmploymentType.FullTime)
                .industry("Technology")
                .fromDate(LocalDate.of(2015, 3, 1))
                .isCurrent(Boolean.TRUE)
                .monthsAtEmployment(130)
                .build();

        Income income = Income.builder()
                .incomeId("INC-1")
                .source(IncomeSource.Employment)
                .type("Salary")
                .amount(new BigDecimal("25000.00"))
                .frequency(Frequency.Monthly)
                .employmentId("EMP-1")
                .build();

        Expense expense = Expense.builder()
                .expenseId("EXP-1")
                .type(ExpenseType.Housing)
                .amount(new BigDecimal("6000.00"))
                .frequency(Frequency.Monthly)
                .build();

        Liability liability = Liability.builder()
                .liabilityId("LIA-1")
                .category(LiabilityCategory.VehicleFinance)
                .balance(new BigDecimal("50000.00"))
                .limit(new BigDecimal("80000.00"))
                .monthlyRepayment(new BigDecimal("2500.00"))
                .isJointAccount(Boolean.FALSE)
                .arrears(BigDecimal.ZERO)
                .source(LiabilitySource.Bureau)
                .build();

        BureauResponse bureauResponse = BureauResponse.builder()
                .bureauResponseId("BR-1")
                .bureauName(BureauName.MERIDIAN_BUREAU)
                .checkType(BureauCheckType.ConsumerCredit)
                .dateOfProvision(LocalDate.of(2026, 10, 1))
                .numericBureauScore(new BigDecimal("712"))
                .isIdentityMatch(Boolean.TRUE)
                .identityMatchConfidence(new BigDecimal("97.5"))
                .isPEPsMatch(Boolean.FALSE)
                .bankCheckResult(BankCheckResultCode.BC01)
                .businessStartDate(LocalDate.of(2015, 3, 1))
                .companyRegistrationStatus(CompanyRegistrationStatus.Active)
                .directors(List.of(director))
                .correlationId("corr-1")
                .isSuccessful(Boolean.TRUE)
                .build();

        RequiredDocumentType requiredDocumentType = RequiredDocumentType.builder()
                .type(DocumentTypeCode.BankStatement)
                .isClassificationRequired(Boolean.TRUE)
                .maxAgeDays(60)
                .build();

        RequiredDocument requiredDocument = RequiredDocument.builder()
                .id("RD-1")
                .partyId("PARTY-1")
                .documentPurpose(DocumentPurpose.POI)
                .reasonCode("AFF03")
                .supportedDocumentTypes(List.of(requiredDocumentType))
                .build();

        SubmittedDocument submittedDocument = SubmittedDocument.builder()
                .documentId("DOC-1")
                .partyId("PARTY-1")
                .requiredDocumentId("RD-1")
                .documentType(DocumentTypeCode.BankStatement)
                .fileName("statement.pdf")
                .link("https://docs.example.com/statement.pdf")
                .status(SubmittedDocumentStatus.Verified)
                .isClassified(Boolean.TRUE)
                .isClassificationRequired(Boolean.TRUE)
                .isRequired(Boolean.TRUE)
                .addedOn(LocalDateTime.of(2026, 10, 15, 9, 30))
                .classificationConfidence(new BigDecimal("0.97"))
                .build();

        DecisionReason decisionReason = DecisionReason.builder()
                .ruleId("AFF03")
                .decisionName("IncomeClarityAssessment")
                .partyId("PARTY-1")
                .proposedStatus(DecisionStatus.REFER)
                .checkType(DecisionCheckCategory.Affordability)
                .internalReason("Declared income gives insufficient margin")
                .externalReason("We need a bit more information about your income")
                .rank(1)
                .build();

        IncomeClarityAssessment incomeClarityAssessment = IncomeClarityAssessment.builder()
                .declaredIncome(new BigDecimal("25000.00"))
                .estimatedRepayment(new BigDecimal("5200.00"))
                .incomeToRepaymentRatio(new BigDecimal("4.8"))
                .isIncomeClarityRequired(Boolean.TRUE)
                .reasonCodes(List.of(decisionReason))
                .build();

        PartyDecisions partyDecisions = PartyDecisions.builder()
                .customerKYCStatus(DecisionStatus.APPROVE)
                .customerPEPsStatus(DecisionStatus.APPROVE)
                .customerCreditStatus(DecisionStatus.APPROVE)
                .creditStatusOverride(CreditStatusOverrideValue.NONE)
                .bureauScoreBand(BureauScoreBand.BAND_B)
                .bankCheckResult(BankCheckResult.MATCH)
                .bankAccountValidity(BankAccountValidity.APPROVE)
                .incomeClarity(incomeClarityAssessment)
                .creditEligibility(DecisionStatus.REFER)
                .requiredDocuments(List.of(requiredDocument))
                .build();

        Person person = Person.builder()
                .firstName("Applicant")
                .lastName("One")
                .dateOfBirth(LocalDate.of(1990, 6, 15))
                .gender("Female")
                .maritalStatus("Single")
                .nationality("ZA")
                .residentialStatus(ResidentialStatus.Owner)
                .numberOfDependents(1)
                .employments(List.of(employment))
                .declaredIncomes(List.of(income))
                .declaredExpenses(List.of(expense))
                .liabilities(List.of(liability))
                .build();

        Company company = Company.builder()
                .legalName("Example Trading (Pty) Ltd")
                .tradingName("Example Trading")
                .registrationNumber("2015/123456/07")
                .taxNumber("TAX-1")
                .companyType(CompanyType.PrivateCompany)
                .industrySector("Retail")
                .incorporationDate(LocalDate.of(2015, 3, 1))
                .yearsTrading(new BigDecimal("11.6"))
                .annualTurnover(new BigDecimal("1200000.00"))
                .numberOfEmployees(8)
                .registeredAddressId("ADDR-1")
                .liabilities(List.of(liability))
                .build();

        Party party = Party.builder()
                .partyId("PARTY-1")
                .partyType(PartyType.Consumer)
                .partyRole(PartyRole.Applicant)
                .isPrimary(Boolean.TRUE)
                .person(person)
                .company(company)
                .addresses(List.of(address))
                .phones(List.of(phone))
                .emails(List.of(email))
                .identifications(List.of(identification))
                .bankAccounts(List.of(bankAccount))
                .bureauResponses(List.of(bureauResponse))
                .decisions(partyDecisions)
                .build();

        BureauDataRequest bureauDataRequest = BureauDataRequest.builder()
                .partyId("PARTY-1")
                .checkType(BureauCheckType.Compliance)
                .bureau(BureauName.SENTINEL_COMPLIANCE_SCREENING)
                .service("screen")
                .build();

        ExternalDataRequired externalDataRequired = ExternalDataRequired.builder()
                .isBureauRequired(Boolean.TRUE)
                .isDocumentDataRequired(Boolean.FALSE)
                .bureauDataRequests(List.of(bureauDataRequest))
                .build();

        DocumentReviewStatus documentReviewStatus = DocumentReviewStatus.builder()
                .isAutoClassificationComplete(Boolean.TRUE)
                .isAutoVerificationComplete(Boolean.TRUE)
                .requiresManualReview(Boolean.FALSE)
                .reasons(List.of(decisionReason))
                .build();

        NetIncome netIncome = NetIncome.builder()
                .declaredIncome(new BigDecimal("25000.00"))
                .detectedIncome(new BigDecimal("24500.00"))
                .totalIncome(new BigDecimal("24500.00"))
                .incomeBase(IncomeBase.DETECTED)
                .build();

        Affordability affordability = Affordability.builder()
                .totalIncome(new BigDecimal("24500.00"))
                .totalExpenses(new BigDecimal("6000.00"))
                .totalLiabilityRepayments(new BigDecimal("2500.00"))
                .disposableIncome(new BigDecimal("16000.00"))
                .requestedRepayment(new BigDecimal("5200.00"))
                .affordabilityStatus(AffordabilityStatus.PASS)
                .reasonCodes(List.of(decisionReason))
                .build();

        ApplicationDecisions applicationDecisions = ApplicationDecisions.builder()
                .creditEligibility(DecisionStatus.REFER)
                .externalDataRequired(externalDataRequired)
                .requiredDocuments(List.of(requiredDocument))
                .documentReviewStatus(documentReviewStatus)
                .netIncome(netIncome)
                .affordability(affordability)
                .applicationRouting(ApplicationRouting.GET_DOCUMENTS)
                .reasons(List.of(decisionReason))
                .build();

        ApplicationError applicationError = ApplicationError.builder()
                .errorId("ERR-1")
                .code("INT-MB-01")
                .message("Meridian Bureau call failed after retries")
                .source(ErrorSource.Integration)
                .serviceName("Meridian Bureau")
                .partyId("PARTY-1")
                .occurredOn(LocalDateTime.of(2026, 10, 30, 11, 0))
                .isResolved(Boolean.FALSE)
                .retryCount(1)
                .build();

        return Application.builder()
                .applicationId("APP-1")
                .applicationTimestamp(LocalDateTime.of(2026, 10, 30, 9, 0))
                .submittedOn(LocalDateTime.of(2026, 10, 30, 9, 5))
                .status(ApplicationStatus.AWAITING_DOCUMENTS)
                .channel(Channel.Online)
                .countryCode("ZA")
                .currency("ZAR")
                .requestType(RequestType.FULL_ORIGINATION)
                .customerType(PartyType.Consumer)
                .productRequests(List.of(productRequest))
                .parties(List.of(party))
                .documents(List.of(submittedDocument))
                .manualCreditDecision(ManualCreditDecision.NONE)
                .manualBankDecision(ManualBankDecision.NONE)
                .isCancelled(Boolean.FALSE)
                .hasCreditApproval(Boolean.FALSE)
                .decisions(applicationDecisions)
                .errors(List.of(applicationError))
                .drsCycleCount(1)
                .build();
    }
}
