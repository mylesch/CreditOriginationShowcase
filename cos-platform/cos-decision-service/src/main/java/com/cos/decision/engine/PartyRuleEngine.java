package com.cos.decision.engine;

import com.cos.domain.model.Application;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Evaluates every table-style, Party-scope decision (Customer PEPs Status, Bureau Score Band,
 * Customer Credit Status, Bank Check Result, Bank Account Validity, as of this slice) for one
 * Party, in a fresh KieSession scoped to that party alone.
 *
 * Running one party per session (rather than one session for the whole Application) means no
 * BureauResponse/Liability/Identification needs a partyId correlation key to be matched
 * correctly - the session only ever contains one party's facts - mirroring the Decision
 * Workbook's own framing of these decisions as "evaluated once per party".
 */
@Component
public class PartyRuleEngine {

    private final KieContainer kieContainer;

    public PartyRuleEngine(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public PartyDecisions evaluate(Application application, Party party) {
        PartyDecisions decisions = new PartyDecisions();
        try (KieSession session = kieContainer.newKieSession()) {
            session.insert(application);
            session.insert(party);
            session.insert(decisions);

            Optional.ofNullable(party.getPerson()).ifPresent(session::insert);
            Optional.ofNullable(party.getCompany()).ifPresent(session::insert);

            insertAll(session, party.getIdentifications());
            insertAll(session, party.getBureauResponses());
            if (party.getPerson() != null) {
                insertAll(session, party.getPerson().getLiabilities());
            }
            if (party.getCompany() != null) {
                insertAll(session, party.getCompany().getLiabilities());
            }

            session.fireAllRules();
        }
        return decisions;
    }

    private void insertAll(KieSession session, Iterable<?> facts) {
        if (facts != null) {
            facts.forEach(session::insert);
        }
    }
}
