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
 * <p>{@link #FIRST_DAY}/{@link #LAST_DAY}/{@link #RANDOM_MID} are unconditionally positive (the
 * app must accept the save); {@link #CROSS_YEAR_INVALID} is unconditionally negative (the app
 * must reject the save with a specific, asserted validation message). {@link #FUTURE_DATE} is
 * NOT unconditionally positive — confirmed live the app accepts a future-dated document within
 * the same accounting year only for a Bank-type Cash/Bank ledger; for a Cash-type ledger it
 * rejects the save ("Post dated entries are not allowed for cash transactions"). General
 * Voucher's own {@code GeneralVoucherEditCombinations} models this as two separate,
 * deterministic variants (see {@code GeneralVoucherEditVariant#requiredCashBankType}) rather
 * than a single ambiguous one — see "Round 22" of the GV plan (FIN-GV-EDIT-HEADER-04).
 */
public enum VoucherEditDateVariant {
    FIRST_DAY,
    LAST_DAY,
    RANDOM_MID,
    CROSS_YEAR_INVALID,
    /** Still inside the SAME accounting year, but after today. Whether the app accepts or
     *  rejects this depends on the claimed candidate's Cash/Bank ledger type — see this enum's
     *  own class Javadoc and {@code GeneralVoucherEditVariant#requiredCashBankType} — it is NOT
     *  unconditionally positive despite the name. */
    FUTURE_DATE
}
