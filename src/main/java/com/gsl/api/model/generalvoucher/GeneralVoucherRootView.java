package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code result} of {@code GET Finance/api/vouchers/generalvouchers/rootview?code=<vchcode>&connectedOUCode=&connectedUserCode=}
 * — the full voucher as the UI loads it for view/edit. Shape confirmed via HAR ({@code GVVoucherbydocno…},
 * {@code GVVoucherRevertDocPage…}). The cash/bank ledger arrives as {@code cashBankLedger{glCode, glName, abbreviation,
 * acountNumber(sic), balance, type, sourceType}}; the lines reuse {@link GeneralVoucherDetailLine}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherRootView {
    private long code;
    private long empCode;
    private long connectedOUCode;
    private GeneralVoucherOwnerSite ownerSite;
    private GeneralVoucherDocSchemeRef docScheme;
    private CashBankLedgerLookupResult cashBankLedger;
    private String voucherDate;
    private int yCode;
    private String docNumber;
    private String reference;
    private String remarks;
    /** Header {@code dataVersion} — required by the update call. */
    private long dataVersion;
    @JsonProperty("isReadOnlyInstance")
    private boolean readOnlyInstance;
    private String readOnlyReason;
    private double netAmount;
    /** {@code P} Posted / {@code U} Unposted. */
    private String documentStatus;
    private String createdBy;
    private String createdOn;
    private String lastModifiedBy;
    private String releasedBy;
    private List<GeneralVoucherDetailLine> expenseIncomeDetail;

    public boolean isPosted()   { return "P".equalsIgnoreCase(documentStatus); }
    public boolean isUnposted() { return "U".equalsIgnoreCase(documentStatus); }
}
