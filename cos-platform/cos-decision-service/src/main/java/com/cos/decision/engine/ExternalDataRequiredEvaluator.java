package com.cos.decision.engine;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.RequestType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauDataRequest;
import com.cos.domain.model.ExternalDataRequired;
import com.cos.domain.model.Party;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** External Data Required (Decision Workbook v0.2) - ruleset-style, hand-written Java. */
@Component
public class ExternalDataRequiredEvaluator {

    public ExternalDataRequired evaluate(Application application) {
        List<BureauDataRequest> requests = new ArrayList<>();
        RequestType requestType = application.getRequestType();
        if (application.getParties() != null) {
            for (Party party : application.getParties()) {
                Set<BureauCheckType> present = presentCheckTypes(party);
                boolean consumer = party.getPartyType() == PartyType.Consumer;

                if (requestType == RequestType.CREDIT_CHECK || requestType == RequestType.FULL_ORIGINATION) {
                    BureauCheckType identityCheck = consumer ? BureauCheckType.KYC : BureauCheckType.KYB;
                    if (!present.contains(identityCheck)) {
                        requests.add(request(party.getPartyId(), identityCheck, BureauName.NORTHGATE_IDENTITY_SERVICES));
                    }
                    BureauCheckType creditCheck = consumer ? BureauCheckType.ConsumerCredit : BureauCheckType.CommercialCredit;
                    if (!present.contains(creditCheck)) {
                        requests.add(request(party.getPartyId(), creditCheck, BureauName.MERIDIAN_BUREAU));
                    }
                    if (!present.contains(BureauCheckType.Compliance)) {
                        requests.add(request(party.getPartyId(), BureauCheckType.Compliance, BureauName.SENTINEL_COMPLIANCE_SCREENING));
                    }
                }
                if (requestType == RequestType.BANK_CHECK || requestType == RequestType.FULL_ORIGINATION) {
                    if (!present.contains(BureauCheckType.BankCheck)) {
                        requests.add(request(party.getPartyId(), BureauCheckType.BankCheck, BureauName.CLEARBANK_VERIFICATION_SERVICE));
                    }
                }
            }
        }
        return ExternalDataRequired.builder()
                .isBureauRequired(!requests.isEmpty())
                .isDocumentDataRequired(Boolean.FALSE)
                .bureauDataRequests(requests)
                .build();
    }

    private Set<BureauCheckType> presentCheckTypes(Party party) {
        if (party.getBureauResponses() == null) {
            return Set.of();
        }
        return party.getBureauResponses().stream()
                .map(br -> br.getCheckType())
                .filter(checkType -> checkType != null)
                .collect(Collectors.toSet());
    }

    private BureauDataRequest request(String partyId, BureauCheckType checkType, BureauName bureau) {
        return BureauDataRequest.builder().partyId(partyId).checkType(checkType).bureau(bureau).build();
    }
}
