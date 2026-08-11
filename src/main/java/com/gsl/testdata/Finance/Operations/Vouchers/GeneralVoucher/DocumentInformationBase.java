package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds optional fields that must not be scanned by XlsxReader's field mapper.
 * XlsxReader calls getDeclaredFields() on the concrete target class only — inherited
 * fields are invisible to it, so this field never appears as a required Excel column.
 *
 * <p>{@code ownersite} is deliberately NOT sourced from Excel — it's resolved live via the
 * logged-in user's site, in both DB mode ({@code DbGeneralVoucherDataSource}) and Excel mode
 * ({@code ExcelGeneralVoucherDataSource}) — and populated via {@link #setOwnersite(String)}.
 * (Note: unlike {@code APVoucher.DocumentInformationBase}, AP's {@code ownersite} is actually a
 * plain, visible Excel column on the concrete class — this class's live-resolution approach is
 * GV-specific, not a shared idiom.)
 */
public abstract class DocumentInformationBase {

    @Getter
    @Setter
    private String ownersite;
}
