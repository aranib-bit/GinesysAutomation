package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

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
