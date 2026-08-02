package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** SI/Save's {result, success, error} envelope — flattened via delegating getters. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferOutCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    public String getSiCode()       { return result != null ? result.siCode : null; }
    public String getSchemeDocno()  { return result != null ? result.schemeDocno : null; }

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
