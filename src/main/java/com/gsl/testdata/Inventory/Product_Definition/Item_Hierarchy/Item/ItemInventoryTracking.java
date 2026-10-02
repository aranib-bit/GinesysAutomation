package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.ExpiryManagementConverter;
import com.gsl.converter.InventoryManagementConverter;
import com.gsl.converter.PricingManagementConverter;
import com.gsl.converter.ValidityModeConverter;
import com.gsl.enums.ExpiryManagementType;
import com.gsl.enums.InventoryManagementType;
import com.gsl.enums.PricingManagementType;
import com.gsl.enums.ValidityModeType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

/**
 * Fixture-data counterpart of the "Inventory Tracking" sheet of the Inventory Item workbook
 * ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — how the item is tracked:
 * inventory/pricing/expiry management mode, validity mode and period. Optional sheet: {@code
 * MergedInvItemDataSupplier} joins a row in only when one exists for the {@code TestCaseID}
 * (unlike the mandatory {@link ItemGeneral}). Feeds {@code
 * MergedInventoryItemData.itemInventoryTracking}.
 */
@Getter
@Setter
@Sheet(name = "Inventory Tracking")
public class ItemInventoryTracking {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "Inventory Management",converter = InventoryManagementConverter.class)
    public InventoryManagementType inventoryManagement;

    @Column(name = "Pricing Management",converter = PricingManagementConverter.class)
    public PricingManagementType pricingManagement;

    @Column(name = "Expiry Management",converter = ExpiryManagementConverter.class)
    private ExpiryManagementType expiryManagement;

    @Column(name = "Validity Mode",converter =  ValidityModeConverter.class)
    public ValidityModeType validityMode;

    @Column(name = "Validity Period")
    public String validityPeriod;
}
