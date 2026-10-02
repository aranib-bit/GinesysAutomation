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

    /** The newly created voucher's own record code (e.g. {@code "610051"} in the confirmed HAR
     *  capture) — parsed by callers such as {@code GeneralVoucherApiBackfillStrategy}'s AP/AR
     *  equivalents to look the voucher up by primary key in the DB. Null when {@link #result} is null. */
    public String getVoucherCode() { return result != null ? result.code : null; }
    /** The voucher's human-facing document scheme string (e.g. {@code "VP/02453/26-27"}). Null when {@link #result} is null. */
    public String getDocScheme()   { return result != null ? result.docScheme : null; }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String docScheme;
        /** The created voucher's own record code, despite the generic JSON field name — see {@link #getVoucherCode()}. */
        private String code;
    }
}
