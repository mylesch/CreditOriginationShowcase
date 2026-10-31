package com.cos.orchestration.process;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauDataRequest;
import com.cos.domain.model.Company;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Liability;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.orchestration.client.ApplicationErrors;
import com.cos.orchestration.client.BureauCallOutcome;
import com.cos.orchestration.client.ClearbankVerificationClient;
import com.cos.orchestration.client.MeridianBureauClient;
import com.cos.orchestration.client.NorthgateKybClient;
import com.cos.orchestration.client.NorthgateKycClient;
import com.cos.orchestration.client.SentinelComplianceClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Process Workbook, "Gather External Data" tab: calls every outstanding bureau for every party
 * concurrently (bounded concurrency of 5), then merges all results back onto the Application on
 * the calling thread once every call has completed - see {@link BureauCallOutcome}'s javadoc for
 * why the merge itself is single-threaded.
 */
@Service
public class GatherExternalDataService {

    private final NorthgateKycClient northgateKycClient;
    private final NorthgateKybClient northgateKybClient;
    private final MeridianBureauClient meridianBureauClient;
    private final SentinelComplianceClient sentinelComplianceClient;
    private final ClearbankVerificationClient clearbankVerificationClient;
    private final ExecutorService bureauCallExecutor;

    public GatherExternalDataService(NorthgateKycClient northgateKycClient, NorthgateKybClient northgateKybClient,
                                       MeridianBureauClient meridianBureauClient,
                                       SentinelComplianceClient sentinelComplianceClient,
                                       ClearbankVerificationClient clearbankVerificationClient,
                                       ExecutorService bureauCallExecutor) {
        this.northgateKycClient = northgateKycClient;
        this.northgateKybClient = northgateKybClient;
        this.meridianBureauClient = meridianBureauClient;
        this.sentinelComplianceClient = sentinelComplianceClient;
        this.clearbankVerificationClient = clearbankVerificationClient;
        this.bureauCallExecutor = bureauCallExecutor;
    }

    public void gather(Application application) {
        List<BureauDataRequest> requests = application.getDecisions() == null
                || application.getDecisions().getExternalDataRequired() == null
                ? List.of() : application.getDecisions().getExternalDataRequired().getBureauDataRequests();
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Map<String, Party> partiesById = application.getParties().stream()
                .collect(Collectors.toMap(Party::getPartyId, Function.identity(), (a, b) -> a));

        List<CompletableFuture<BureauCallOutcome>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(
                        () -> call(application, partiesById.get(request.getPartyId()), request), bureauCallExecutor))
                .toList();

        List<BureauCallOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

        for (BureauCallOutcome outcome : outcomes) {
            merge(partiesById.get(outcome.partyId()), application, outcome);
        }
    }

    private BureauCallOutcome call(Application application, Party party, BureauDataRequest request) {
        BureauCheckType checkType = request.getCheckType();
        return switch (checkType) {
            case KYC -> northgateKycClient.verify(application, party);
            case KYB -> northgateKybClient.verify(application, party);
            case ConsumerCredit, CommercialCredit -> meridianBureauClient.fetchCreditReport(application, party);
            case Compliance -> sentinelComplianceClient.screen(application, party);
            case BankCheck -> clearbankVerificationClient.checkAccount(application, party);
        };
    }

    private void merge(Party party, Application application, BureauCallOutcome outcome) {
        if (!outcome.isSuccess()) {
            ApplicationErrors.append(application, outcome.error());
            return;
        }
        if (party.getBureauResponses() == null) {
            party.setBureauResponses(new ArrayList<>());
        }
        party.getBureauResponses().add(outcome.bureauResponse());

        if (outcome.identificationVerifiedOverride() != null) {
            firstIdentification(party).ifPresent(id -> id.setIsVerified(outcome.identificationVerifiedOverride()));
        }
        if (!outcome.liabilitiesToMerge().isEmpty()) {
            mergeLiabilities(party, outcome);
        }
        if (outcome.yearsTradingOverride() != null && party.getCompany() != null) {
            party.getCompany().setYearsTrading(outcome.yearsTradingOverride());
        }
    }

    private void mergeLiabilities(Party party, BureauCallOutcome outcome) {
        if (party.getCompany() != null) {
            Company company = party.getCompany();
            List<Liability> liabilities =
                    company.getLiabilities() == null ? new ArrayList<>() : new ArrayList<>(company.getLiabilities());
            liabilities.addAll(outcome.liabilitiesToMerge());
            company.setLiabilities(liabilities);
        } else if (party.getPerson() != null) {
            Person person = party.getPerson();
            List<Liability> liabilities =
                    person.getLiabilities() == null ? new ArrayList<>() : new ArrayList<>(person.getLiabilities());
            liabilities.addAll(outcome.liabilitiesToMerge());
            person.setLiabilities(liabilities);
        }
    }

    private Optional<Identification> firstIdentification(Party party) {
        return party.getIdentifications() == null || party.getIdentifications().isEmpty()
                ? Optional.empty() : Optional.of(party.getIdentifications().get(0));
    }
}
