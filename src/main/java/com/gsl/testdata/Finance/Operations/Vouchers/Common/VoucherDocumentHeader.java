package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import java.time.LocalDate;

/**
 * Common document header fields shared by AR, AP, General, and Contra Vouchers.
 * All four voucher types have these fields with identical meaning.
 * Implement this on each voucher's DocumentInformation POJO so that
 * VoucherDocumentInformationBase and FinVchMainBaseValidator can work
 * against a single type instead of repeating logic per voucher.
 */
public interface VoucherDocumentHeader {
    LocalDate getDocumentdate();
    String getReference();
    String getRemarks();
    String getCashbank();
    String getOwnersite();
    String getVoucherno();
}
