package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * What an API-mode Revert/Delete/Edit data source knows about the voucher it created, carried on the test data so the test can act on it
 * with {@code db.available=no} — the DB flows otherwise re-derive these from {@code finvchmain}/{@code finvchdet}/{@code fintag}.
 */
@Value
@Builder
public class ApiVoucherRef {
    /** {@code finvchmain.vchcode}. */
    long vchcode;
    /** {@code scheme_docno}. */
    String docNo;
    /** The voucher's sub-ledger ({@code slcode}), needed by the untag call. */
    long slCode;
    /** The voucher's own amount, needed by the untag call. */
    BigDecimal amount;
    /** Organizational unit the voucher was created under. */
    int ouCode;
    /** True when it was created adjusting documents, i.e. it must be untagged before Revert/Delete/Edit. */
    boolean tagged;
    /** The documents a tagged voucher was created adjusting - what the DB flows read from fintag before the untag, and the
     *  Edit flow needs to restore the adjustment afterwards. Empty when untagged. */
    @Builder.Default
    List<CapturedTag> capturedTags = List.of();
}
