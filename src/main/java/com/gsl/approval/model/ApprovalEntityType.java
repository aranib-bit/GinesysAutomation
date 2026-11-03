package com.gsl.approval.model;

import java.util.Arrays;

/**
 * The approval-capable entities Ginesys exposes ({@code ApprovalRule/GetEntities}), keyed by the entity code the approval APIs use. This is
 * only a registry of codes: whether approval is enabled is always read live from the application, never from here. Adding a document type is
 * one constant here (plus, from Phase 3, its rule-variable enum and facts provider).
 */
public enum ApprovalEntityType {
    AP("AP", "AP Voucher"),
    PO("PO", "Purchase Order"),
    PSO("PSO", "Service Order"),
    SO("SO", "Sales Order/Transfer Order"),
    POI("POI", "Purchase Indent");

    private final String code;
    private final String displayName;

    ApprovalEntityType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    /** The entity for an API code; throws on a code this framework does not know, so a typo never reads as "approval disabled". */
    public static ApprovalEntityType fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval entity code '" + code + "'"));
    }
}
