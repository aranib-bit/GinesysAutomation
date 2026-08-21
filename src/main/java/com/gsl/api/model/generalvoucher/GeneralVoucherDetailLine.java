package com.gsl.api.model.generalvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One Expense/Income Detail GL row of a {@link GeneralVoucherCreateRequest} — <b>provisional</b>,
 * field names/shape modeled on {@code ArVoucherDetailLine} (GL row instead of Sub Ledger row,
 * since General Voucher's Expense/Income Detail grid posts directly against a GL, optionally with
 * a Site/Sub Ledger allocation — see {@code GlLineType}), not confirmed against a real HAR capture.
 * Correct this class's fields once a "create General Voucher" HAR is available — see
 * {@code GeneralVoucherApiClient}'s class Javadoc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVoucherDetailLine {

    @Builder.Default
    private int code = 0;

    private long glCode;
    private String glName;

    /** {@code "D"} or {@code "C"} — mirrors {@code ArVoucherDetailLine.paymentMode}'s single-letter convention. */
    private String debitCreditFlag;

    private double amount;

    /** Non-null only for {@code TYPE3_SITE_AND_SUBLEDGER} rows. */
    private Long subLedgerCode;
    private String subLedgerName;

    /** Non-null only for {@code TYPE2_SITE_ONLY}/{@code TYPE3_SITE_AND_SUBLEDGER} rows. */
    private Long siteCode;
    private String siteName;

    @Builder.Default
    private boolean isReversal = false;

    private String narration;
}
