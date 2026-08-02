package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DC/Save's {result, success, error} envelope — flattened via delegating getters. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    public String getDcCode()          { return result != null ? result.dcCode : null; }
    public String getPacketBarcode()   { return result != null ? result.packetBarcode : null; }

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
