package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;

/**
 * One row of the "SelectItem" sheet in the receipts workbook — the item-search detail rows joined
 * onto an {@link ItemBlock} by {@code MergedReceiptDataSupplier} when that block's {@link
 * ItemDetailsRow#getItemSelectionMajorMethod()} is {@code "Item"} and {@link
 * ItemDetailsRow#getItemSetSelectionWay()} is {@code "Select Item"}. {@link #searchKeyword1}/
 * {@link #searchValue1} drive the item-search field/value used to locate the item in the UI.
 */
@Sheet(name="SelectItem")
public class SelectItemRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "SearchKeyword1")
    private String searchKeyword1;
    @Column(name = "SearchValue1")
    private String searchValue1;
    @Column(name = "Quantity")
    private String quantity;

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getQuantity() {
        return quantity;
    }

    public String getSearchKeyword1() {
        return searchKeyword1;
    }

    public String getSearchValue1() {
        return searchValue1;
    }
}
