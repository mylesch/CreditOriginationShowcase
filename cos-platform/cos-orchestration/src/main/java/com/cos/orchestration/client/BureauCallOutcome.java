package com.cos.orchestration.client;

import com.cos.domain.model.ApplicationError;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Liability;

import java.math.BigDecimal;
import java.util.List;

/**
 * The immutable result of one bureau call, returned by a worker thread in
 * {@code GatherExternalDataService}'s bounded-concurrency fan-out. Side-channel mutations that a
 * bureau's response implies beyond its own {@code BureauResponse} record (new liabilities,
 * {@code Company.yearsTrading}, {@code Identification.isVerified}) are carried here rather than
 * applied directly to the shared {@code Party}, so the actual merge can happen single-threaded
 * once every concurrent call has completed - see the Process Workbook's "Aggregate Results into
 * Application" step.
 */
public record BureauCallOutcome(String partyId, BureauResponse bureauResponse, ApplicationError error,
                                 List<Liability> liabilitiesToMerge, BigDecimal yearsTradingOverride,
                                 Boolean identificationVerifiedOverride) {

    public static BureauCallOutcome success(String partyId, BureauResponse bureauResponse) {
        return new BureauCallOutcome(partyId, bureauResponse, null, List.of(), null, null);
    }

    public static BureauCallOutcome success(String partyId, BureauResponse bureauResponse,
                                              List<Liability> liabilitiesToMerge, BigDecimal yearsTradingOverride) {
        return new BureauCallOutcome(partyId, bureauResponse, null, liabilitiesToMerge, yearsTradingOverride, null);
    }

    public static BureauCallOutcome success(String partyId, BureauResponse bureauResponse,
                                              Boolean identificationVerifiedOverride) {
        return new BureauCallOutcome(partyId, bureauResponse, null, List.of(), null, identificationVerifiedOverride);
    }

    public static BureauCallOutcome failure(String partyId, ApplicationError error) {
        return new BureauCallOutcome(partyId, null, error, List.of(), null, null);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
