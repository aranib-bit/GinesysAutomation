package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** ServiceInvoice/Save's {result, success, error} envelope — flattened via delegating getters. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceInvoiceAdhocCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    public String getInvoiceCode()     { return result != null ? result.invoiceCode : null; }
    public String getSchemeDocno()     { return result != null ? result.schemeDocNo : null; }
    /** Server's own claim of posted/unposted status — cross-checked against finpost.release_status
     *  via FinPostDao in PendingApDocumentBackfillStrategy rather than trusted blindly. */
    public String getReleaseStatus()   { return result != null ? result.releaseStatus : null; }
    public List<String> getValidationErrors() { return result != null ? result.validationErrors : null; }

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
