package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response envelope of {@code POST/PUT Finance/api/vouchers/general} — confirmed via HAR:
 * {@code {"status":0,"message":"…","result":{"docScheme":"VG/00410/26-27","code":1183324},"dataVersion":11462411,"success":true}}.
 * Naming quirk: {@code result.docScheme} holds the generated <b>document number</b>; {@code result.code} is the vchcode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherCreateResponse {

    private int status;
    private String message;
    private boolean success;
    private String error;
    private Result result;
    private Long dataVersion;

    /** {@code result.code} (the voucher's vchcode) as text, or null if the response has no result. */
    public String getVoucherCode() { return result != null ? result.code : null; }

    /** {@code result.code} as a long; throws if absent. */
    public long vchcode() {
        if (result == null || result.code == null) {
            throw new IllegalStateException("General Voucher response has no result.code: " + this);
        }
        return Long.parseLong(result.code);
    }

    /** {@code result.docScheme} — the generated document number — or null if the response has no result. */
    public String getDocScheme() { return result != null ? result.docScheme : null; }

    /** Raw shape of the response's {@code result} object. */
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
