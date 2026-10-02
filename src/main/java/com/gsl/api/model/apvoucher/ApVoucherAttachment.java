package com.gsl.api.model.apvoucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST Finance/api/vouchers/ap's {@code attachment} field — confirmed via HAR:
 * {@code {"doccode":0,"foldername":"0","Files":[]}}. Deliberately a separate class from
 * {@code com.gsl.api.model.serviceinvoiceadhoc.AttachedFiles} (used by ArVoucherCreateRequest) —
 * that class's shape ({@code timestamp/docType/docCode/files}) does not match this endpoint's
 * actual field names, so reusing it would silently send the wrong JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApVoucherAttachment {
    /** Always {@code 0} in both confirmed HAR captures — no attachment doc has been raised yet. */
    @Builder.Default
    private int doccode = 0;
    /** Literal string {@code "0"} (not a numeric folder id) — kept as the HAR-confirmed literal
     *  rather than guessing a real folder name/id. */
    @Builder.Default
    private String foldername = "0";
    /** Always empty in both captures — no files are attached by this client's Customer/Vendor
     *  Advance flow. */
    @JsonProperty("Files")
    @Builder.Default
    private List<Object> files = List.of();
}
