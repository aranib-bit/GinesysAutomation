package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code POST /SI/Save}'s {@code {result, success, error}} envelope — returned by
 * {@code TransferOutApiClient.createTransferOut(TransferOutCreateRequest)} and validated by
 * {@code TransferOutApiValidator}. The generated Transfer Out identifiers live one level down in
 * {@link Result}; {@link #getSiCode()}/{@link #getSchemeDocno()} flatten them onto this class so
 * callers (e.g. {@code TransferOutApiScenarioType}) don't need to reach into {@link #result}
 * directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferOutCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** Delegates to {@link Result#siCode}, or {@code null} if {@link #result} is absent. */
    public String getSiCode()       { return result != null ? result.siCode : null; }
    /** Delegates to {@link Result#schemeDocno}, or {@code null} if {@link #result} is absent. */
    public String getSchemeDocno()  { return result != null ? result.schemeDocno : null; }

    /**
     * The created Transfer Out's identifiers — {@code result} in the raw JSON. {@link #siCode} is
     * mapped from the wire field {@code code} (renamed here to avoid colliding with the outer
     * {@code TransferOutCreateRequest.code} concept and to read as what it actually identifies —
     * the created SI/Transfer Out document).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("code")
        private String siCode;
        private String schemeDocno;
    }
}
