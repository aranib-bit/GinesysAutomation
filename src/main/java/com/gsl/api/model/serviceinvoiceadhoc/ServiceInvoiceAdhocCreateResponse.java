package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response of {@code POST ServiceInvoice/Save} — a {@code {result, success, error}} envelope,
 * returned by {@link com.gsl.api.client.ServiceInvoiceAdhocApiClient#createServiceInvoiceAdhoc}
 * and checked by {@link com.gsl.api.validator.ServiceInvoiceAdhocApiValidator#validate}. The
 * nested {@link Result} fields are exposed at this top level via delegating getters (each
 * null-safe against a {@code null result}) so callers don't need to null-check/unwrap it themselves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceInvoiceAdhocCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** The created document's own code (wire key {@code result.code} — see {@link Result#invoiceCode}); {@code null} if {@code result} is {@code null}. */
    public String getInvoiceCode()     { return result != null ? result.invoiceCode : null; }
    /** The created document's scheme doc number; {@code null} if {@code result} is {@code null}. */
    public String getSchemeDocno()     { return result != null ? result.schemeDocNo : null; }
    /** Server's own claim of posted/unposted status — cross-checked against finpost.release_status
     *  via FinPostDao in PendingApDocumentBackfillStrategy rather than trusted blindly. */
    public String getReleaseStatus()   { return result != null ? result.releaseStatus : null; }
    /** Any non-fatal validation messages the server returned alongside a successful save; {@code null} if {@code result} is {@code null}. */
    public List<String> getValidationErrors() { return result != null ? result.validationErrors : null; }

    /** {@code Save}'s nested {@code result} object — the created document's identifiers and post-save status. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("code")
        private String invoiceCode;
        /** Real JSON key is "schemeDocNo" (capital N) — was "schemeDocno" here, always null. */
        private String schemeDocNo;
        private String releaseStatus;
        private List<String> validationErrors;
    }
}
