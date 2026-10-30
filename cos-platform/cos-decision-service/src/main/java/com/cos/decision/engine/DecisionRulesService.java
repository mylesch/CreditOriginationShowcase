package com.cos.decision.engine;

import com.cos.domain.model.Application;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import org.springframework.stereotype.Service;

/**
 * Top-level DRS entry point. Implements the Call-DRS process tab's "Map DRS Response" contract
 * for the decisions covered so far: evaluates each Party against the table-style decisions
 * (via PartyRuleEngine) and the Party Required Documents ruleset (via
 * PartyRequiredDocumentsEvaluator), writing results onto Party.decisions.
 *
 * <p>Not yet implemented (next increment): Customer KYC/KYB Status, Credit Status Override,
 * Income Clarity Assessment, Party Credit Eligibility (remaining Party-scope decisions), and
 * every Application-scope decision (External Data Required, Document Review Status, Required
 * Documents (App), Net Income, Affordability, Credit Eligibility (App), Application Routing).
 */
@Service
public class DecisionRulesService {

    private final PartyRuleEngine partyRuleEngine;
    private final PartyRequiredDocumentsEvaluator requiredDocumentsEvaluator;

    public DecisionRulesService(PartyRuleEngine partyRuleEngine,
                                 PartyRequiredDocumentsEvaluator requiredDocumentsEvaluator) {
        this.partyRuleEngine = partyRuleEngine;
        this.requiredDocumentsEvaluator = requiredDocumentsEvaluator;
    }

    public Application evaluate(Application application) {
        if (application.getParties() != null) {
            for (Party party : application.getParties()) {
                PartyDecisions decisions = partyRuleEngine.evaluate(application, party);
                party.setDecisions(decisions);
                decisions.setRequiredDocuments(requiredDocumentsEvaluator.evaluate(application, party));
            }
        }
        return application;
    }
}
