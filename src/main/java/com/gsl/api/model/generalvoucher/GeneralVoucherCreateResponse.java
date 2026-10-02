package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code Finance/api/vouchers/gv}'s response envelope — <b>provisional</b>, shape mirrors
 * {@code ArVoucherCreateResponse} ({@code success}/{@code error}/{@code result.docScheme,code}).
 * Confirm against a real HAR capture — see {@code GeneralVoucherCreateRequest}'s class Javadoc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** {@code result.code} (the created voucher's document code), or null if the response has no result. */
    public String getVoucherCode() { return result != null ? result.code : null; }
    /** {@code result.docScheme}, or null if the response has no result. */
    public String getDocScheme()   { return result != null ? result.docScheme : null; }

    /** Raw shape of the response's {@code result} object — only the fields consumed here. */
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
