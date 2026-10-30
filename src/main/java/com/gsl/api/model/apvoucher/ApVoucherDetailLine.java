package com.gsl.api.model.apvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AP Voucher detail line — shape confirmed via HAR (Finance/api/vouchers/ap Save request's
 * {@code apVoucherDetail[]}), from both the TDS and No-TDS captures. TDS fields are flat siblings
 * on the line, not a nested object, and are simply null/false when {@code deductTDS} is false:
 * <pre>
 * TDS:    deductTDS=true,  tdsRate=2.22222, tdsTypeCode=3, tdsAppAmt=3000, tdsAmt=66.67,
 *         tdsGrossAmt=3000, tdsRoundOffAmt=67, netPayable=2933 (= amount)
 * No-TDS: deductTDS=false, tdsRate=null, tdsTypeCode=null, tdsAppAmt=null, tdsAmt=null,
 *         tdsGrossAmt=null, tdsRoundOffAmt=null, netPayable=null
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApVoucherDetailLine {
    /** Null for a new line being created; would carry the existing line's code when editing one. */
    private Integer code;
    private long subLedgerCode;
    private String subLedgerName;
    private long ledgerCode;
    @Builder.Default
    private String generalLedgerAbbre = "";
    private String generalLedgerGroupName;
    private long referenceSiteCode;
    @Builder.Default
    private String ledgerNarration = "";
    @Builder.Default
    private boolean isReversal = false;
    /** Net amount posted to the cash/bank GL — equals {@code netPayable} when TDS applies. */
    private double amount;

    /** The actual TDS switch for this line. */
    @Builder.Default
    private boolean deductTDS = false;
    private Double tdsRate;
    private Long tdsTypeCode;
    private Double tdsAppAmt;
    private Double tdsAmt;
    private Double tdsGrossAmt;
    private Double tdsRoundOffAmt;
    private Double netPayable;
    @Builder.Default
    private boolean useExceptionRate = false;

    /** 'C' in both captures. The instrument fields below are consequently always left null in
     *  every observed request, since this flow's payment mode never varies from that value. */
    @Builder.Default
    private String paymentMode = "C";
    private String bookCode;
    private String instrumentNumber;
    private String instrumentDate;
    private String chequeLabel;
    private String paymentReference;
    /** Optimistic-concurrency stamp — {@code 0} for a newly created line. */
    @Builder.Default
    private int dataVersion = 0;
    /** Null for the Customer/Vendor Advance flow; set when the line adjusts pending documents (the voucher is then tagged
     *  against them) — see {@link ApVoucherAdjustmentDetail}. */
    private java.util.List<ApVoucherAdjustmentDetail> apVoucherAdjustmentDetail;
}
