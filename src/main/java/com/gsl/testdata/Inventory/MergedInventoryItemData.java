package com.gsl.testdata.Inventory;

import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One merged, invocation-ready data-provider row for an Inventory Item creation test — pairs the
 * generic {@link MasterTestData} (test-case metadata) with the item's mandatory {@link
 * ItemGeneral} row and its optional per-sheet joins ({@link ItemCategoryDescription}, {@link
 * ItemOthers}, {@link ItemPricing}, {@link ItemInventoryTracking}, {@link ItemPOSBehaviour} —
 * {@code null} when that sheet has no row for this {@code TestCaseID}) plus its 0..N {@link
 * ItemStoreWiseMultiplePrice} rows, and this row's position within the current test method's
 * total invocations (see {@link InvocationData}). Assembled by {@code
 * com.gsl.utils.dataprovider.MergedInvItemDataSupplier}, which also runs {@code
 * MandatoryFieldValidator#validate} against every non-null joined sheet before emitting it.
 */
@Data
@Builder
@AllArgsConstructor
public class MergedInventoryItemData implements InvocationData {

    private MasterTestData masterTestData;
    private ItemGeneral itemGeneral;
    private ItemCategoryDescription itemCategoryDescription;
    private ItemOthers itemOthers;
    private ItemPricing itemPricing;
    private ItemInventoryTracking itemInventoryTracking;
    private ItemPOSBehaviour itemPOSBehaviour;
    private List<ItemStoreWiseMultiplePrice> storeWiseMultiplePriceList;

    // invocation metadata
    private int invocationIndex;
    private int totalInvocations;

    /** This row's 1-based position among {@link #totalInvocations} — see {@link InvocationData}. */
    @Override
    public int getInvocationIndex() { return invocationIndex; }

    /** Total invocation count for this {@code TestCaseID}, from {@code MasterTestData.getInvocationcount()} — see {@link InvocationData}. */
    @Override
    public int getTotalInvocations() { return totalInvocations; }
}
