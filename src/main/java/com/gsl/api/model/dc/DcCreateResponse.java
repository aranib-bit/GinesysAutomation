package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DC/Save's {@code {result, success, error}} envelope, deserialized whole — consumed by
 * {@code DcApiClient.createDc(DcCreateRequest)} and validated by {@code DcApiValidator}. The
 * actual saved document's code/barcode live under {@link #getResult()}; the delegating getters
 * below let callers write {@code response.getDcCode()} without reaching through
 * {@code getResult()} first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** {@code result.code} (the created DC's document code), or null if the response has no result. */
    public String getDcCode()          { return result != null ? result.dcCode : null; }
    /** {@code result.packetBarcode}, or null if the response has no result. */
    public String getPacketBarcode()   { return result != null ? result.packetBarcode : null; }

    /** Raw shape of DC/Save's {@code result} object — only the fields this client consumes. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("code")
        private String dcCode;
        private String packetBarcode;
    }
}
