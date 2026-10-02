package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.InventoryItemConverter;
import com.gsl.converter.MaterialTypeConverter;
import com.gsl.converter.NegativeStockMethodConverter;
import com.gsl.enums.InventoryItemType;
import com.gsl.enums.MaterialTypeType;
import com.gsl.enums.NegativeStockMethodType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

/**
 * Fixture-data counterpart of the "Others" sheet of the Inventory Item workbook ({@code
 * ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — the item's naming, vendor/ledger,
 * tax/material classification, and unit-of-measurement fields. Optional sheet: {@code
 * MergedInvItemDataSupplier} joins a row in only when one exists for the {@code TestCaseID}
 * (unlike the mandatory {@link ItemGeneral}). Feeds {@code MergedInventoryItemData.itemOthers}.
 */
@Getter
@Setter
@Sheet(name = "Others")
public class ItemOthers {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "Item Name")
    public String itemName;

    @Column(name = "Short Name")
    public String shortName;

    @Column(name = "Vendor Name")
    public String vendorName;

    @Column(name = "Tax Group")
    public String taxGroup;

    @Column(name = "Material Type",converter =  MaterialTypeConverter.class)
    public MaterialTypeType materialType;

    @Column(name = "Cost Sheet")
    public String costSheet;

    @Column(name = "Expiry Date")
    public String expiryDate;

    @Column(name = "General Ledger")
    public String generalLedger;

    @Column(name = "Sub Ledger")
    public String subLedger;

    @Column(name = "Unit of Measurement")
    public String unitOfMeasurement;

    @Column(name = "Negative Stock Method",converter =  NegativeStockMethodConverter.class)
    public NegativeStockMethodType negativeStockMethod;

    @Column(name = "Inventory Item",converter = InventoryItemConverter.class)
    public InventoryItemType inventoryItem;

    @Column(name = "Scan Unit")
    public String scanUnit;

    @Column(name = "Number1")
    public String number1;

    @Column(name = "Number2")
    public String number2;

    @Column(name = "Number3")
    public String number3;

    @Column(name = "Remarks")
    public String remarks;
}
