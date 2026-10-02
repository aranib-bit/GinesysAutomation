package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.SafeDateConverter;
import java.time.LocalDate;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.util.Date;

/**
 * Fixture-data counterpart of the "Store Wise Multiple Price" sheet of the Inventory Item
 * workbook ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — one row per store
 * that overrides the item's default {@link ItemPricing} RSP/MRP from a given effective date. The
 * only one of the seven Item-hierarchy sheets joined as a <b>list</b> (0..N rows) rather than a
 * single optional row: {@code MergedInvItemDataSupplier} collects every row matching the {@code
 * TestCaseID} into {@code MergedInventoryItemData.storeWiseMultiplePriceList}.
 */
@Getter
@Sheet(name = "Store Wise Multiple Price")
public class ItemStoreWiseMultiplePrice {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "StoreName")
    public String storename;

    @Column(name = "Initial")
    public String storeinitial;

    @Column(name = "Effective Date",converter = SafeDateConverter.class)
    public LocalDate  effectiveDate;

    @Column(name = "RSP",converter = SafeDoubleConverter.class)
    public double rsp;

    @Column(name = "MRP",converter = SafeDoubleConverter.class)
    public double mrp;
}
