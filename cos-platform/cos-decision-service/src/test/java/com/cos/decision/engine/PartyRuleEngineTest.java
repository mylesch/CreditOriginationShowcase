package com.cos.decision.engine;

import com.cos.domain.enums.BankAccountValidity;
import com.cos.domain.enums.BankCheckResult;
import com.cos.domain.enums.BankCheckResultCode;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauScoreBand;
import com.cos.domain.enums.CompanyRegistrationStatus;
import com.cos.domain.enums.CreditStatusOverrideValue;
import com.cos.domain.enums.DecisionStatus;
import com.cos.domain.enums.EmploymentType;
import com.cos.domain.enums.Frequency;
import com.cos.domain.enums.LiabilityCategory;
import com.cos.domain.enums.LiabilitySource;
import com.cos.domain.enums.ManualBankDecision;
import com.cos.domain.enums.ManualCreditDecision;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Company;
import com.cos.domain.model.Employment;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Income;
import com.cos.domain.model.Liability;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.Person;
import com.cos.domain.model.PersonReference;
import com.cos.domain.model.ProductRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.KieServices;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every rule row of the five Drools decision tables implemented so far (Customer PEPs
 * Status, Bureau Score Band, Customer Credit Status, Bank Check Result, Bank Account Validity) -
 * the "100% rule coverage" goal from the implementation plan, for this slice of decisions.
 */
class PartyRuleEngineTest {

    private final PartyRuleEngine engine =
            new PartyRuleEngine(KieServices.Factory.get().getKieClasspathContainer());

    // -- Customer PEPs Status --------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "false, APPROVE",
            "true,  REFER",
    })
    void customerPepsStatus(boolean isPepsMatch, DecisionStatus expected) {
        Party party = partyWithBureauResponse(BureauResponse.builder()
                .bureauResponseId("BR-1")
                .checkType(BureauCheckType.Compliance)
                .isPEPsMatch(isPepsMatch)
                .build());
        PartyDecisions result = engine.evaluate(application(), party);
        assertEquals(expected, result.getCustomerPEPsStatus());
    }

    // -- Bureau Score Band --------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "750, BAND_A",
            "749, BAND_B",
            "650, BAND_B",
            "649, BAND_C",
            "550, BAND_C",
            "549, BAND_D",
            "450, BAND_D",
            "449, BAND_E",
    })
    void bureauScoreBand(int score, BureauScoreBand expected) {
        Party party = partyWithBureauResponse(BureauResponse.builder()
                .bureauResponseId("BR-1")
                .numericBureauScore(BigDecimal.valueOf(score))
                .build());
        PartyDecisions result = engine.evaluate(application(), party);
        assertEquals(expected, result.getBureauScoreBand());
    }

    // -- Customer Credit Status -----------------------------------------------------------

    @Test
    void customerCreditStatus_seriousArrearsAlwaysDeclinesRegardlessOfBand() {
        Party party = Party.builder().partyId("P1").build();
        party.setPerson(Person.builder()
                .liabilities(List.of(Liability.builder()
                        .liabilityId("L1")
                        .category(LiabilityCategory.UnsecuredLoan)
                        .arrears(new BigDecimal("500"))
                        .monthlyRepayment(new BigDecimal("200"))
                        .source(LiabilitySource.Bureau)
                        .build()))
                .build());
        party.setBureauResponses(List.of(BureauResponse.builder()
                .bureauResponseId("BR-1").numericBureauScore(new BigDecimal("800")).build()));

        PartyDecisions result = engine.evaluate(application(), party);
        assertEquals(DecisionStatus.DECLINE, result.getCustomerCreditStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "800, APPROVE",
            "700, APPROVE",
            "600, REFER",
            "500, DECLINE",
            "400, DECLINE",
    })
    void customerCreditStatus_byBandWithNoArrears(int score, DecisionStatus expected) {
        Party party = partyWithBureauResponse(BureauResponse.builder()
                .bureauResponseId("BR-1").numericBureauScore(BigDecimal.valueOf(score)).build());
        PartyDecisions result = engine.evaluate(application(), party);
        assertEquals(expected, result.getCustomerCreditStatus());
    }

    // -- Bank Check Result ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "BC01, MATCH",
            "BC02, NO_MATCH",
            "BC03, NO_MATCH",
            "BC04, NO_MATCH",
            "BC05, NO_MATCH",
            "BC06, PENDING",
    })
    void bankCheckResult(BankCheckResultCode code, BankCheckResult expected) {
        Party party = partyWithBureauResponse(BureauResponse.builder()
                .bureauResponseId("BR-1")
                .checkType(BureauCheckType.BankCheck)
                .bankCheckResult(code)
                .build());
        PartyDecisions result = engine.evaluate(application(), party);
        assertEquals(expected, result.getBankCheckResult());
    }

    // -- Bank Account Validity --------------------------------------------------------------

    @Test
    void bankAccountValidity_manualApprove() {
        assertEquals(BankAccountValidity.APPROVE,
                bankAccountValidity(ManualBankDecision.APPROVE, null));
    }

    @Test
    void bankAccountValidity_manualDecline() {
        assertEquals(BankAccountValidity.REFER,
                bankAccountValidity(ManualBankDecision.DECLINE, null));
    }

    @ParameterizedTest
    @CsvSource({
            "BC01, APPROVE",
            "BC04, REFER",
            "BC06, REFER",
    })
    void bankAccountValidity_noOverride(BankCheckResultCode code, BankAccountValidity expected) {
        assertEquals(expected, bankAccountValidity(ManualBankDecision.NONE, code));
    }

    private BankAccountValidity bankAccountValidity(ManualBankDecision manualBankDecision, BankCheckResultCode code) {
        Application app = application();
        app.setManualBankDecision(manualBankDecision);
        Party party = code == null
                ? Party.builder().partyId("P1").build()
                : partyWithBureauResponse(BureauResponse.builder()
                        .bureauResponseId("BR-1").checkType(BureauCheckType.BankCheck).bankCheckResult(code).build());
        PartyDecisions result = engine.evaluate(app, party);
        return result.getBankAccountValidity();
    }

    // -- Credit Status Override --------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "APPROVE, APPROVE",
            "DECLINE, DECLINE",
            "REFER,   REFER",
            "NONE,    NONE",
    })
    void creditStatusOverride(ManualCreditDecision manualCreditDecision, CreditStatusOverrideValue expected) {
        Application app = application();
        app.setManualCreditDecision(manualCreditDecision);
        PartyDecisions result = engine.evaluate(app, Party.builder().partyId("P1").build());
        assertEquals(expected, result.getCreditStatusOverride());
    }

    // -- Customer KYC Status ------------------------------------------------------------------

    @Test
    void customerKycStatus_noMatchDeclines() {
        assertEquals(DecisionStatus.DECLINE, customerKycStatus(false, null, null));
    }

    @Test
    void customerKycStatus_expiredIdentificationRefers() {
        assertEquals(DecisionStatus.REFER,
                customerKycStatus(true, new BigDecimal("97"), LocalDate.now().minusDays(1)));
    }

    @ParameterizedTest
    @CsvSource({
            "97, APPROVE",
            "85, REFER",
            "70, DECLINE",
    })
    void customerKycStatus_byConfidenceWhenNotExpired(BigDecimal confidence, DecisionStatus expected) {
        assertEquals(expected, customerKycStatus(true, confidence, LocalDate.now().plusYears(1)));
    }

    private DecisionStatus customerKycStatus(boolean isIdentityMatch, BigDecimal confidence, LocalDate expiresOn) {
        Party party = Party.builder().partyId("P1")
                .bureauResponses(List.of(BureauResponse.builder()
                        .bureauResponseId("BR-1")
                        .checkType(BureauCheckType.KYC)
                        .isIdentityMatch(isIdentityMatch)
                        .identityMatchConfidence(confidence)
                        .build()))
                .identifications(List.of(Identification.builder().identificationId("I1").expiresOn(expiresOn).build()))
                .build();
        return engine.evaluate(application(), party).getCustomerKYCStatus();
    }

    // -- Customer KYB Status ------------------------------------------------------------------

    @Test
    void customerKybStatus_noMatchDeclines() {
        assertEquals(DecisionStatus.DECLINE, customerKybStatusByRegStatus(false, CompanyRegistrationStatus.Active, null));
    }

    @ParameterizedTest
    @CsvSource({
            "Deregistered, DECLINE",
            "InLiquidation, DECLINE",
            "Unknown, REFER",
    })
    void customerKybStatus_byRegistrationStatus(CompanyRegistrationStatus status, DecisionStatus expected) {
        assertEquals(expected, customerKybStatusByRegStatus(true, status, null));
    }

    @Test
    void customerKybStatus_activeButNoRepresentativeAuthority() {
        assertEquals(DecisionStatus.REFER,
                customerKybStatusByRegStatus(true, CompanyRegistrationStatus.Active, new BigDecimal("97")));
    }

    @ParameterizedTest
    @CsvSource({
            "97, APPROVE",
            "85, REFER",
            "70, DECLINE",
    })
    void customerKybStatus_authorityConfirmedByConfidence(BigDecimal confidence, DecisionStatus expected) {
        assertEquals(expected, customerKybStatusWithAuthority(confidence));
    }

    private DecisionStatus customerKybStatusByRegStatus(boolean isIdentityMatch, CompanyRegistrationStatus status,
                                                          BigDecimal confidenceIfNoAuthorityCase) {
        Party business = Party.builder().partyId("P1").partyType(PartyType.Business)
                .bureauResponses(List.of(BureauResponse.builder()
                        .bureauResponseId("BR-1")
                        .checkType(BureauCheckType.KYB)
                        .isIdentityMatch(isIdentityMatch)
                        .identityMatchConfidence(confidenceIfNoAuthorityCase == null ? new BigDecimal("97") : confidenceIfNoAuthorityCase)
                        .companyRegistrationStatus(status)
                        .directors(List.of())
                        .build()))
                .build();
        Application app = application();
        app.setParties(List.of(business));
        return engine.evaluate(app, business).getCustomerKYBStatus();
    }

    private DecisionStatus customerKybStatusWithAuthority(BigDecimal confidence) {
        Party representative = Party.builder().partyId("P2").partyType(PartyType.Consumer)
                .partyRole(PartyRole.Representative)
                .identifications(List.of(Identification.builder().identificationId("I2").reference("REP-ID-1").build()))
                .build();
        Party business = Party.builder().partyId("P1").partyType(PartyType.Business)
                .bureauResponses(List.of(BureauResponse.builder()
                        .bureauResponseId("BR-1")
                        .checkType(BureauCheckType.KYB)
                        .isIdentityMatch(true)
                        .identityMatchConfidence(confidence)
                        .companyRegistrationStatus(CompanyRegistrationStatus.Active)
                        .directors(List.of(PersonReference.builder()
                                .fullName("Rep Name")
                                .identificationNumber("REP-ID-1")
                                .build()))
                        .build()))
                .build();
        Application app = application();
        app.setParties(List.of(business, representative));
        return engine.evaluate(app, business).getCustomerKYBStatus();
    }

    // -- Income Clarity Assessment ------------------------------------------------------------

    @Test
    void incomeClarity_businessAlwaysRequiresDocuments() {
        Party business = Party.builder().partyId("P1").partyType(PartyType.Business)
                .company(Company.builder().annualTurnover(new BigDecimal("1200000")).build())
                .build();
        Application app = applicationWithProduct(new BigDecimal("10000"), 36);
        PartyDecisions result = engine.evaluate(app, business);
        assertTrue(result.getIncomeClarity().getIsIncomeClarityRequired());
    }

    @Test
    void incomeClarity_selfEmployedAlwaysRequiresDocuments() {
        Party party = consumerWithIncomeAndEmployment(new BigDecimal("20000"), EmploymentType.SelfEmployed);
        Application app = applicationWithProduct(new BigDecimal("10000"), 36);
        assertTrue(engine.evaluate(app, party).getIncomeClarity().getIsIncomeClarityRequired());
    }

    @Test
    void incomeClarity_fullTimeHighIncomeIsClear() {
        Party party = consumerWithIncomeAndEmployment(new BigDecimal("50000"), EmploymentType.FullTime);
        Application app = applicationWithProduct(new BigDecimal("5000"), 12);
        assertFalse(engine.evaluate(app, party).getIncomeClarity().getIsIncomeClarityRequired());
    }

    @Test
    void incomeClarity_fullTimeLowIncomeRequiresDocuments() {
        Party party = consumerWithIncomeAndEmployment(new BigDecimal("100"), EmploymentType.FullTime);
        Application app = applicationWithProduct(new BigDecimal("20000"), 36);
        assertTrue(engine.evaluate(app, party).getIncomeClarity().getIsIncomeClarityRequired());
    }

    @Test
    void incomeClarity_noEmploymentRecordRequiresDocuments() {
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder().build())
                .build();
        Application app = applicationWithProduct(new BigDecimal("10000"), 36);
        assertTrue(engine.evaluate(app, party).getIncomeClarity().getIsIncomeClarityRequired());
    }

    private Party consumerWithIncomeAndEmployment(BigDecimal monthlyIncome, EmploymentType employmentType) {
        return Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder()
                        .declaredIncomes(List.of(Income.builder()
                                .incomeId("INC-1").amount(monthlyIncome).frequency(Frequency.Monthly).build()))
                        .employments(List.of(Employment.builder()
                                .employmentId("EMP-1").employmentType(employmentType).isCurrent(true).build()))
                        .build())
                .build();
    }

    private Application applicationWithProduct(BigDecimal amount, int termMonths) {
        Application app = application();
        app.setProductRequests(List.of(ProductRequest.builder()
                .productRequestId("PR-1").requestedAmount(amount).termMonths(termMonths).build()));
        return app;
    }

    // -- Party Credit Eligibility -------------------------------------------------------------

    @Test
    void partyCreditEligibility_manualOverrideApproveWinsOutright() {
        assertEquals(DecisionStatus.APPROVE,
                partyCreditEligibility(ManualCreditDecision.APPROVE, 400, true));
    }

    @Test
    void partyCreditEligibility_manualOverrideDeclineWinsOutright() {
        assertEquals(DecisionStatus.DECLINE,
                partyCreditEligibility(ManualCreditDecision.DECLINE, 800, false));
    }

    @Test
    void partyCreditEligibility_noOverride_creditDeclineWins() {
        assertEquals(DecisionStatus.DECLINE,
                partyCreditEligibility(ManualCreditDecision.NONE, 400, false));
    }

    @Test
    void partyCreditEligibility_noOverride_pepsMatchRefers() {
        assertEquals(DecisionStatus.REFER,
                partyCreditEligibility(ManualCreditDecision.NONE, 800, true));
    }

    @Test
    void partyCreditEligibility_noOverride_allCleanApproves() {
        assertEquals(DecisionStatus.APPROVE,
                partyCreditEligibility(ManualCreditDecision.NONE, 800, false));
    }

    private DecisionStatus partyCreditEligibility(ManualCreditDecision manualCreditDecision, int bureauScore, boolean pepsMatch) {
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .bureauResponses(List.of(
                        BureauResponse.builder().bureauResponseId("BR-KYC").checkType(BureauCheckType.KYC)
                                .isIdentityMatch(true).identityMatchConfidence(new BigDecimal("97")).build(),
                        BureauResponse.builder().bureauResponseId("BR-CREDIT").checkType(BureauCheckType.ConsumerCredit)
                                .numericBureauScore(BigDecimal.valueOf(bureauScore)).build(),
                        BureauResponse.builder().bureauResponseId("BR-PEPS").checkType(BureauCheckType.Compliance)
                                .isPEPsMatch(pepsMatch).build()))
                .identifications(List.of(Identification.builder().identificationId("I1")
                        .expiresOn(LocalDate.now().plusYears(1)).build()))
                .build();
        Application app = application();
        app.setManualCreditDecision(manualCreditDecision);
        return engine.evaluate(app, party).getCreditEligibility();
    }

    @Test
    void noFactsMeansNoDecisionsFire() {
        PartyDecisions result = engine.evaluate(application(), Party.builder().partyId("P1").build());
        assertNull(result.getCustomerPEPsStatus());
        assertNull(result.getBureauScoreBand());
    }

    private Party partyWithBureauResponse(BureauResponse response) {
        return Party.builder().partyId("P1").bureauResponses(List.of(response)).build();
    }

    private Application application() {
        return Application.builder().applicationId("APP-1").build();
    }
}
