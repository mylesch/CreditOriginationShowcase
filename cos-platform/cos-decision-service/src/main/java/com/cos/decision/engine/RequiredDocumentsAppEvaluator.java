package com.cos.decision.engine;

import com.cos.domain.model.Application;
import com.cos.domain.model.Party;
import com.cos.domain.model.RequiredDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Required Documents (App) (Decision Workbook v0.2) - ruleset-style, hand-written Java. */
@Component
public class RequiredDocumentsAppEvaluator {

    public List<RequiredDocument> evaluate(Application application) {
        if (application.getParties() == null) {
            return List.of();
        }
        List<RequiredDocument> all = application.getParties().stream()
                .filter(p -> p.getDecisions() != null && p.getDecisions().getRequiredDocuments() != null)
                .flatMap(p -> p.getDecisions().getRequiredDocuments().stream())
                .collect(Collectors.toList());

        List<RequiredDocument> deduped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RequiredDocument rd : all) {
            String key = rd.getPartyId() + "|" + rd.getDocumentPurpose() + "|" + rd.getSupportedDocumentTypes();
            if (seen.add(key)) {
                deduped.add(rd);
            }
        }
        return deduped;
    }
}
