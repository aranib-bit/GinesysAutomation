package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Represents the On Account selection on the AP Voucher header.
 *
 * <p>Controls which sub-ledger grid columns are rendered in the DOM and therefore
 * which XPaths the framework may send to the browser:
 * <ul>
 *   <li>{@link #ADVANCE} — On Account = Yes. The {@code deductTDS} column is present;
 *       TDS applicability is governed by the vendor.</li>
 *   <li>{@link #DOCUMENT} — On Account = No. The {@code deductTDS} column is never
 *       rendered; all TDS XPaths must be suppressed to avoid {@code NoSuchElementException}.</li>
 * </ul>
 */
public enum OnAccountMode {

    /** On Account = Yes (advance payment). deductTDS column is present in the DOM. */
    ADVANCE,

    /** On Account = No (document adjustment). deductTDS column is absent from the DOM. */
    DOCUMENT
}
