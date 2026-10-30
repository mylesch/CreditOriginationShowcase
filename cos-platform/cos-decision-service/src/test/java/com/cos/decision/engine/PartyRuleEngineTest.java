package com.cos.decision.engine;

import com.cos.domain.enums.BankAccountValidity;
import com.cos.domain.enums.BankCheckResult;
import com.cos.domain.enums.BankCheckResultCode;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauScoreBand;
import com.cos.domain.enums.DecisionStatus;
import com.cos.domain.enums.LiabilityCategory;
import com.cos.domain.enums.LiabilitySource;
import com.cos.domain.enums.ManualBankDecision;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Liability;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
