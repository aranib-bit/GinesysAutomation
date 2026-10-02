package com.gsl.testdata.Procurement;

/**
 * Common shape for one scanned/selected line-item's detail data during a Goods Receipt, with one
 * implementation per {@code ItemType} row value: {@link ItemItemDetail}/{@link ScanItemAdapter}
 * ("Item"), {@link BatchItemDetail} ("Batch"), {@link SerialItemDetail} ("Serial"), and {@link
 * RegularItemDetail} ("Regular") — built by {@link ItemDetailFactory#create}. Consumed via {@code
 * instanceof} dispatch on {@link #getItemType()} (see {@code
 * com.tests.Procurement.Operations.ReceiptsDataTest}).
 *
 * <p>{@code TODO-confirm:} this whole interface/variant/factory chain (plus {@link
 * ScanItemAdapter}, {@link ItemDetailFactory}, {@link ItemDetailsData}, {@link ScanItem}, {@link
 * ScanItemWithDetails}) has no confirmed live construction path — {@link ItemDetailFactory#create}
 * is never called anywhere in the codebase (confirmed by repo-wide search), and its sole consumer,
 * the legacy {@code com.gsl.testdata.MergedReceiptData}, is likewise never constructed anywhere;
 * appears to be an orphaned parallel to the actually-wired {@code com.gsl.testdata.dto} sheet
 * classes used by {@code MergedReceiptDataSupplier}.
 */
public interface ItemDetail {
    /** The originating {@code TestCaseID} this detail row belongs to. */
    String getTestCaseId();
    /** Discriminator matching the source row's {@code ItemType} column ("Item"/"Batch"/"Serial"/"Regular") — see {@link ItemDetailFactory#create}. */
    String getItemType();
    /** The row's "Major method" column value (only populated by the "Item"-type variants). */
    String getItemSelectionMajorMethod();
    /** The row's "Item/Set Selection ways" column value (only populated by the "Item"-type variants). */
    String getItemSetSelectionSelectionWays();
}