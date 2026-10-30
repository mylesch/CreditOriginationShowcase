package com.cos.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The four invented external bureaus/services referenced throughout the Integration Workbook. */
public enum BureauName {
    MERIDIAN_BUREAU("Meridian Bureau"),
    NORTHGATE_IDENTITY_SERVICES("Northgate Identity Services"),
    SENTINEL_COMPLIANCE_SCREENING("Sentinel Compliance Screening"),
    CLEARBANK_VERIFICATION_SERVICE("Clearbank Verification Service");

    private final String label;

    BureauName(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static BureauName fromLabel(String label) {
        for (BureauName value : values()) {
            if (value.label.equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown bureauName: " + label);
    }
}
