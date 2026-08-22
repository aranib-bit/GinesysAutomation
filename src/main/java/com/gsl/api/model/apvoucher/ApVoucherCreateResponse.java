package com.gsl.api.model.apvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Finance/api/vouchers/ap's {status, timestamp, message, result, dataVersion, error, success}
 * envelope — mirrors {@code ArVoucherCreateResponse}. Confirmed via HAR:
 * {@code {"result":{"docScheme":"VP/02453/26-27","code":610051}}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApVoucherCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    public String getVoucherCode() { return result != null ? result.code : null; }
    public String getDocScheme()   { return result != null ? result.docScheme : null; }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String docScheme;
        private String code;
    }
}
