package com.gsl.pages.pagecomponent.Finance.Common;

/**
 * Marker interface for any Add/Edit voucher page that owns a {@link CashBankPopup} (e.g. {@code
 * ArVoucherAdd}, {@code ApVoucherAdd}, {@code GeneralVoucherAdd}). {@link CashBankPopup}'s
 * {@code selectCashBank}/{@code cancel} methods return the {@code CashBankParent} instance passed
 * into its constructor, so the caller gets back a correctly-typed reference to its own page
 * object after the popup closes, without a downcast.
 *
 * <p>Declares no methods of its own — unlike {@link AdjustmentEntryParent}, no shared
 * post-processing hook is needed here yet; add one only if a future popup interaction requires it.
 */
public interface CashBankParent {
}
