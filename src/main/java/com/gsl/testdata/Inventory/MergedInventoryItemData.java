package com.gsl.testdata.Inventory;

import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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

    @Override
    public int getInvocationIndex() { return invocationIndex; }

    @Override
    public int getTotalInvocations() { return totalInvocations; }
}
