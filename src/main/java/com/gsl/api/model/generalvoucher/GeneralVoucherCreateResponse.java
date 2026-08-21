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
