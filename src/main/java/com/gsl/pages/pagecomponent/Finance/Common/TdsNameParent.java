package com.gsl.pages.pagecomponent.Finance.Common;

/**
 * Marker interface for any page component that owns a {@link TdsNamePopup} (currently only
 * {@code APVoucherTdsAndNetPayable}). Mirrors {@link CashBankParent}'s role for {@link
 * CashBankPopup}: {@link TdsNamePopup}'s {@code selectTdsName}/{@code cancel} methods return the
 * {@code TdsNameParent} instance passed into its constructor, so the caller gets back a
 * correctly-typed reference to its own page object after the popup closes, without a downcast.
 */
public interface TdsNameParent {
}
