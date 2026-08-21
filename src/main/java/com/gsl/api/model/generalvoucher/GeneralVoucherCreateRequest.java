package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST {@code Finance/api/vouchers/gv} request body — <b>provisional</b>. Modeled on
 * {@code ArVoucherCreateRequest}'s shape (voucher date, doc scheme, owner site, detail lines,
 * save-and-release flag) since no HAR capture of a real "create General Voucher" UI action exists
 * in this codebase yet (unlike AR/AP, whose request shapes were confirmed from a captured HAR —
 * see {@code FinanceApiPattern.GENERAL_VOUCHER}'s "confirm path" flag). Correct every field here
 * (and {@code GeneralVoucherApiClient.createGeneralVoucherWithDefaults}'s orchestration) once that
 * HAR is available — nothing else in the Revert DB-data-source/backfill wiring needs to change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVoucherCreateRequest {

    @Builder.Default
    private int code = 0;

    private String docNumber;
    private String voucherDate;
    private long docSchemeCode;
    private long empCode;
    private long connectedOUCode;
    private long connectedSiteCode;
    private long ownerSiteCode;

    @Builder.Default
    private String reference = "";
    @Builder.Default
    private String remarks = "";

    @Builder.Default
    private List<GeneralVoucherDetailLine> generalVoucherDetail = List.of();

    /** Save-and-release so a backfilled voucher lands Posted immediately, ready for Revert — see AR's own lesson-learned comment. */
    @JsonProperty("IsMarkedForSaveAndRelease")
    @Builder.Default
    private boolean markedForSaveAndRelease = true;

    @JsonProperty("RequestId")
    private String requestId;
}
