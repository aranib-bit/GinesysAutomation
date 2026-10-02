package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * Fixture-data counterpart of the "Pricing" sheet of the Inventory Item workbook ({@code
 * ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — the item's default standard/
 * wholesale/retail/MRP rates (store-independent; contrast with the per-store overrides in {@link
 * ItemStoreWiseMultiplePrice}). Optional sheet: {@code MergedInvItemDataSupplier} joins a row in
 * only when one exists for the {@code TestCaseID} (unlike the mandatory {@link ItemGeneral}).
 * Feeds {@code MergedInventoryItemData.itemPricing}.
 */
@Getter
@Sheet(name = "Pricing")
public class ItemPricing {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "Standard Rate")
    public String standardRate;

    @Column(name = "Whole Sale Price")
    public String wholeSalePrice;

    @Column(name = "Retail Sale Price")
    public String retailSalePrice;

    @Column(name = "Maximum Retail Price")
    public String maximumRetailPrice;
}
