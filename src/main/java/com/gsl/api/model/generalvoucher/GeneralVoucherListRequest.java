package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST Finance/api/vouchers/generalvouchers} (the list/search call, also used for AR/AP under
 * {@code arvouchers}/{@code apvouchers}) — confirmed via HAR. {@code filter} is a JSON <b>string</b>, e.g.
 * {@code [{"operator":"eq","value":"vg/00038/26-27","property":"voucherNumber","validationType":"string"}]}; only
 * {@code voucherNumber eq} has been captured so far.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVoucherListRequest {
    private long connectedSite;
    private long connectedUser;
    @JsonProperty("ConnectedSiteOUCode")
    private long connectedSiteOuCode;
    private String workDate;
    @Builder.Default
    private int criteriaId = 0;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String criteriaLine;
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int start = 0;
    @Builder.Default
    private int limit = 50;
    @Builder.Default
    private String sort = "voucherDate DESC,voucherNumber DESC";
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String filter;

    /** Builds the {@code filter} JSON string for an exact voucher-number match. */
    public static String voucherNumberEquals(String voucherNumber) {
        return "[{\"operator\":\"eq\",\"value\":\"" + voucherNumber.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"property\":\"voucherNumber\",\"validationType\":\"string\"}]";
    }
}
