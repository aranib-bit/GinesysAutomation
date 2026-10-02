package com.gsl.api.model.arvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AR Voucher detail line — shape confirmed via HAR capture (Finance/api/vouchers/ar Save
 * request's {@code arVoucherDetail[]}), built by {@link com.gsl.api.client.ArVoucherApiClient
 * #createArVoucherWithDefaults} from a resolved {@code SubLedgerLookupResult}/
 * {@code CashBankLedgerLookupResult}/{@code ReferenceSiteLookupResult}. AR Voucher has no TDS
 * concept, so unlike {@code ApVoucherDetailLine} this line carries no TDS fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArVoucherDetailLine {
    /** {@code 0} for a new line being created; would carry the existing line's code when editing one. */
    @Builder.Default
    private int code = 0;
    private long subLedgerCode;
    private String subLedgerName;
    /** External sub-ledger id (distinct from {@code subLedgerCode}) — carried alongside the name
     *  so a same-named sub-ledger can be disambiguated, e.g. via {@code FinSlDao.findBySlnameAndSlid}. */
    private String subLedgerId;
    private String subLedgerClassName;
    private Double subLedgerBalance;
    private long ledgerCode;
    private String ledgerName;
    private String ledgerGroupName;
    private String ledgerAbbreviation;
    private String referenceSiteName;
    private long referenceSiteCode;
    private String referenceSiteType;
    private String referenceSiteOUName;
    /** 'C' in the captured HAR example — payment mode for this line. */
    private String paymentMode;
    private String bookCode;
    private String instrumentNumber;
    private String instrumentDate;
    private String chequeLabel;
    /** Bank the cheque is drawn on — only meaningful for a non-Cash {@code paymentMode}, always
     *  null in the captured {@code paymentMode='C'} flow. */
    private String drawnOn;
    private String paymentReference;
    private String ledgerNarration;
    @Builder.Default
    private boolean isReversal = false;
    private double amount;
    /** Always null in the captured flow — no adjustment (e.g. against a pending AP document) is
     *  wired up by this client. */
    private Object arVoucherAdjustmentDetail;
    /** Optimistic-concurrency stamp — {@code 0} for a newly created line. */
    @Builder.Default
    private int dataVersion = 0;
}
