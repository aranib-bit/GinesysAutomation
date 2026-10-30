package com.gsl.api.model.arvoucher;

import com.gsl.api.model.common.DocNumberSchemeEntry;

import com.gsl.api.model.serviceinvoiceadhoc.AttachedFiles;
import com.gsl.api.model.udf.UdfValueEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST Finance/api/vouchers/ar request body — full shape confirmed via HAR
 * (CreateARVoucherstage-ideal.ginesys.cloud4.har). Site/ledger/sub-ledger details are all sourced
 * live from earlier lookup calls (docnumberschemes, cashbankledgers, subledgers, sites), never
 * hardcoded — same principle as ServiceInvoiceAdhocCreateRequest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArVoucherCreateRequest {
    /** {@code 0} to create a new voucher; would carry the existing voucher's code when editing one. */
    @Builder.Default
    private int code = 0;
    /** Populated only when the resolved {@code docSchemeCode}'s scheme is manual-numbered (see
     *  {@code DocNumberSchemeEntry#getManual()}); left null otherwise so the server auto-generates it. */
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
    private long cashBankGLCode;
    @Builder.Default
    private List<ArVoucherDetailLine> arVoucherDetail = List.of();
    @Builder.Default
    private boolean isAutoReleaseApplied = false;
    @Builder.Default
    private boolean isReadOnlyInstance = false;
    /**
     * The real "Save & Release" trigger — captured HAR left this false yet the voucher still
     * posted immediately (server-side Auto Release for that user). Set true explicitly anyway,
     * same lesson learned from ServiceInvoiceAdhocApiClient.saveAndRelease.
     */
    @JsonProperty("IsMarkedForSaveAndRelease")
    @Builder.Default
    private boolean markedForSaveAndRelease = true;
    @JsonProperty("RequestId")
    private String requestId;
    @Builder.Default
    private AttachedFiles attachment = AttachedFiles.builder().build();
    @Builder.Default
    private List<UdfValueEntry> udfValues = List.of();
}
