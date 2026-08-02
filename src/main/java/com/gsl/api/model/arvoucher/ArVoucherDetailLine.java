package com.gsl.api.model.arvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AR Voucher detail line — shape confirmed via HAR capture (Finance/api/vouchers/ar Save
 * request's {@code arVoucherDetail[]}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArVoucherDetailLine {
    @Builder.Default
    private int code = 0;
    private long subLedgerCode;
    private String subLedgerName;
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
    private String drawnOn;
    private String paymentReference;
    private String ledgerNarration;
    @Builder.Default
    private boolean isReversal = false;
    private double amount;
    private Object arVoucherAdjustmentDetail;
    @Builder.Default
    private int dataVersion = 0;
}
