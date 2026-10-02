package com.gsl.testdata.Procurement;

import java.util.List;

/**
 * Pairs one {@link ScanItem} row with its {@link ItemDetail} lines (built via {@link
 * ItemDetailFactory#create}) — one element of the legacy {@code
 * com.gsl.testdata.MergedReceiptData#getScanItems()} list, iterated by {@code
 * com.tests.Procurement.Operations.ReceiptsDataTest}.
 *
 * <p>{@code TODO-confirm:} no confirmed constructor call anywhere in the codebase — see {@link
 * ItemDetail}'s Javadoc for the full orphaned-chain finding this class is part of.
 */
public class ScanItemWithDetails {
    private ScanItem scanItem;
    private List<ItemDetail> itemDetails;

    /** Pairs an already-built {@code scanItem} row with its already-built {@code itemDetails} lines. */
    public ScanItemWithDetails(ScanItem scanItem, List<ItemDetail> itemDetails) {
        this.scanItem = scanItem;
        this.itemDetails = itemDetails;
    }

    public ScanItem getScanItem() {
        return scanItem;
    }

    public List<ItemDetail> getItemDetails() {
        return itemDetails;
    }
}
