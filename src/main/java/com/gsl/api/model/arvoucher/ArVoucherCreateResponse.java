package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Finance/api/vouchers/ar's {status, timestamp, message, result, dataVersion, error, success}
 * envelope — status/timestamp/message/dataVersion are ignored (unused downstream, same as
 * ServiceInvoiceAdhocCreateResponse ignores fields it doesn't need).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArVoucherCreateResponse {

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
