package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Which new document date a header-edit scenario assigns, in an accounting-year-aware way — see
 * {@code AccountingPeriodResolver#resolveEditDate} for exactly how each is computed. Kept
 * domain-agnostic (not nested inside {@link GeneralVoucherEditVariant}) for the same reason
 * {@link VoucherEditEntryMethod} is: the same date-variant concept applies identically to AR
 * Voucher's and AP Voucher's own Edit flows, not just General Voucher's — a shared concept
 * belongs in one common place rather than duplicated (or awkwardly imported cross-domain) per
 * voucher type.
 *
 * <p>{@link #FIRST_DAY}/{@link #LAST_DAY}/{@link #RANDOM_MID}/{@link #FUTURE_DATE} are positive
 * (the app must accept the save — confirmed live that a future-dated document within the same
 * accounting year is accepted, not rejected); only {@link #CROSS_YEAR_INVALID} is negative (the
 * app must reject the save with a specific, asserted validation message).
 */
public enum VoucherEditDateVariant {
    FIRST_DAY,
    LAST_DAY,
    RANDOM_MID,
    CROSS_YEAR_INVALID,
    /** Still inside the SAME accounting year, but after today. Confirmed live: the app ACCEPTS
     *  this (no validation rejection) — see {@code AccountingPeriodResolver#resolveEditDate}. */
    FUTURE_DATE
}
