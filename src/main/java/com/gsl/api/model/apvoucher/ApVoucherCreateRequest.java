package com.gsl.api.model.apvoucher;

import com.gsl.api.model.udf.UdfValueEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST Finance/api/vouchers/ap request body — full shape confirmed via HAR (both
 * CreateAPVoucherCustomerAdvanceTDS_ and ...NoTDS_stage-ideal.ginesys.cloud4.har). Site/ledger/
 * sub-ledger details are all sourced live from earlier lookup calls (docnumberschemes,
 * cashbankledgers, subledgers, sites), never hardcoded — same principle as ArVoucherCreateRequest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApVoucherCreateRequest {
    @Builder.Default
    private int code = 0;
    private long empCode;
    private long connectedOUCode;
    private long ownerSiteCode;
    private long connectedSiteCode;
    /**
     * Doc scheme code for the auto-created TDS journal (menusysshortcode=FinanceTDSJournal,
     * confirmed via HAR as code=171, name="TDS Journal") — null when {@code deductTDS} is false
     * on every line.
     */
    private Long autoCreatedJournalDocSchemeCode;
    private long docSchemeCode;
    private String docNumber;
    private long cashBankGLCode;
    private String voucherDate;
    @Builder.Default
    private int yCode = 0;
    private String remarks;
    @Builder.Default
    private String documentStatus = "N";
    /** Always true for the on-account Customer/Vendor Advance flow this client builds. */
    @Builder.Default
    private boolean onAccount = true;
    @Builder.Default
    private List<ApVoucherDetailLine> apVoucherDetail = List.of();
    @Builder.Default
    private boolean isReadOnlyInstance = false;
    @Builder.Default
    private boolean isAutoReleaseApplied = false;
    /**
     * Captured HAR left this false on both captures yet the voucher still posted immediately
     * (server-side Auto Release for that user, same lesson learned building ArVoucherApiClient) —
     * kept false here to match the literal observed request rather than assuming true like AR's
     * client does.
     */
    @JsonProperty("IsMarkedForSaveAndRelease")
    @Builder.Default
    private boolean markedForSaveAndRelease = false;
    private String requestId;
    @Builder.Default
    private ApVoucherAttachment attachment = ApVoucherAttachment.builder().build();
    @Builder.Default
    private List<UdfValueEntry> udfValues = List.of();
}
