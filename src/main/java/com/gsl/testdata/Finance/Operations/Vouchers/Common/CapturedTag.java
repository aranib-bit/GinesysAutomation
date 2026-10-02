package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import lombok.Value;

import java.math.BigDecimal;

/**
 * One live {@code fintag} row captured BEFORE a voucher is untagged (the tag is destroyed the
 * moment untag succeeds) — the counter-document's own number and the tagged amount, enough to
 * re-apply the same adjustment through the UI after unpost + re-open for Edit.
 */
@Value
public class CapturedTag {
    /** {@code scheme_docno} of the OTHER side of the tag — may be a voucher, a purchase invoice
     *  (PIM), a journal, ... resolved via {@code finpost}, not {@code finvchmain}. */
    String counterDocNo;
    BigDecimal amount;
}
