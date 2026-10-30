package com.cos.decision.engine;

import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationDecisions;
import com.cos.domain.model.DocumentExtraction;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Top-level DRS entry point. Implements the Call-DRS process tab's "Map DRS Response" contract:
 * evaluates every Party-scope decision for each party (PartyRuleEngine, then Party Required
 * Documents), then every Application-scope decision once (ApplicationRuleEngine) - all 18
 * decisions in the Decision Workbook.
 *
 * <p>DocumentExtraction results (from Lumen Document Intelligence) have no home on Application
 * in the BOM, so they are not part of the REST contract yet; {@link #evaluate(Application)}
 * passes an empty list, meaning Document Review Status/Net Income never see detected income
 * until the orchestration layer has a documented way to supply them.
 */
@Service
public class DecisionRulesService {

    private final PartyRuleEngine partyRuleEngine;
    private final PartyRequiredDocumentsEvaluator requiredDocumentsEvaluator;
    private final ApplicationRuleEngine applicationRuleEngine;

    public DecisionRulesService(PartyRuleEngine partyRuleEngine,
                                 PartyRequiredDocumentsEvaluator requiredDocumentsEvaluator,
                                 ApplicationRuleEngine applicationRuleEngine) {
        this.partyRuleEngine = partyRuleEngine;
        this.requiredDocumentsEvaluator = requiredDocumentsEvaluator;
        this.applicationRuleEngine = applicationRuleEngine;
    }

    public Application evaluate(Application application) {
        return evaluate(application, List.of());
    }

    public Application evaluate(Application application, List<DocumentExtraction> documentExtractions) {
        if (application.getParties() != null) {
            for (Party party : application.getParties()) {
                PartyDecisions decisions = partyRuleEngine.evaluate(application, party);
                party.setDecisions(decisions);
                decisions.setRequiredDocuments(requiredDocumentsEvaluator.evaluate(application, party));
            }
        }
        ApplicationDecisions applicationDecisions = applicationRuleEngine.evaluate(application, documentExtractions);
        application.setDecisions(applicationDecisions);
        return application;
    }
}
