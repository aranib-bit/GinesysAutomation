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
