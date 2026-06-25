package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Common read contract for AP and AR SelectDocument rows.
 * Both concrete classes are field-for-field identical; this interface lets the
 * shared report layer iterate them without per-voucher instanceof casts.
 */
public interface VoucherSelectDocRow {
    String getLineNo();
    String getDocumentNumber();
    LocalDate getDocumentDate();
    String getDocumentType();
    BigDecimal getAdjustmentAmount();
}
