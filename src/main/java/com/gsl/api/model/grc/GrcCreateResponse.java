package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GRC/Save's {result, success, error} envelope — result fields flattened via delegating getters. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrcCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    public String getGrcCode()      { return result != null ? result.grcCode : null; }
    public String getSchemeDocno()  { return result != null ? result.schemeDocno : null; }

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
