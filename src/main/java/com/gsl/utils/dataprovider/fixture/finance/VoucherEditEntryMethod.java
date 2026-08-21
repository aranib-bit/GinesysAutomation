package com.gsl.utils.dataprovider.fixture.finance;

/**
 * The two live UI paths for opening an existing voucher for edit — Actions→Edit, or
 * double-clicking the row directly (plan §9.4/9.5). Kept domain-agnostic (not nested inside
 * {@link GeneralVoucherEditVariant}) because the same two entry paths apply identically to
 * AR Voucher and AP Voucher's own future Edit flows, not just General Voucher — a shared
 * concept belongs in one common place rather than duplicated per domain, mirroring how
 * {@code IVoucherGridAction}/{@code CommonVoucherActionsMenu} were pulled up to a common
 * package for the same reason.
 */
public enum VoucherEditEntryMethod {
    ACTIONS_EDIT,
    DOUBLE_CLICK_EDIT;

    /**
     * Alternates the two entry paths by position — even index → {@link #ACTIONS_EDIT}, odd
     * index → {@link #DOUBLE_CLICK_EDIT} — so a fixed-size Edit scenario matrix (GV's 7 today,
     * AR's/AP's own matrices later) exercises both live paths without duplicating any scenario
     * (a 7×2 cross product was considered and rejected — see
     * {@link GeneralVoucherEditCombinations}'s Javadoc).
     *
     * @param zeroBasedIndex the variant/row's position in its own declared order
     */
    public static VoucherEditEntryMethod forIndex(int zeroBasedIndex) {
        return zeroBasedIndex % 2 == 0 ? ACTIONS_EDIT : DOUBLE_CLICK_EDIT;
    }
}
