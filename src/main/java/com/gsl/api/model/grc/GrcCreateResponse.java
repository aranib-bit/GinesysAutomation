package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GRC/Save's {@code {result, success, error}} envelope, deserialized whole — consumed by
 * {@code GrcApiClient.createGrc(GrcCreateRequest)} and validated by {@code GrcApiValidator}.
 * Result fields are flattened via the delegating getters below so callers can write
 * {@code response.getGrcCode()} without reaching through {@code getResult()} first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrcCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** {@code result.code} (the created GRC's document code), or null if the response has no result. */
    public String getGrcCode()      { return result != null ? result.grcCode : null; }
    /** {@code result.schemeDocno}, or null if the response has no result. */
    public String getSchemeDocno()  { return result != null ? result.schemeDocno : null; }

    /** Raw shape of GRC/Save's {@code result} object — only the fields this client consumes. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("code")
        private String grcCode;
        private String schemeDocno;
    }
}
