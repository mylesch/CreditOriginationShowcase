package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** Request/response shapes for Clearbank Verification Service - Bank Account Check. */
public final class ClearbankDtos {

    private ClearbankDtos() {
    }

    public record Request(String accountHolderName, String bankName, String accountNumber, String branchCode,
                           String reference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String reference, String resultCode, String resultDescription, String accountStatus,
                            BigDecimal ownerNameMatchScore, Instant checkedOn, String status, ApiError error) {
    }
}
