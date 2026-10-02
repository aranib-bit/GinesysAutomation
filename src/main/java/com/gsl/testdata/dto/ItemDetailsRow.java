package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;
import lombok.Getter;

/**
 * One row of the "ItemDetails" sheet in the receipts workbook ({@code
 * ConfigFactory.getExcelDataConfig().receiptsPath()}) — read by {@code MergedReceiptDataSupplier}
 * and joined onto an {@link ItemBlock}, one block per matching {@code TestCaseID}. Its two
 * selection-method columns decide which sibling sheet's rows {@code MergedReceiptDataSupplier}
 * joins in for that block:
 * <ul>
 *   <li>{@link #itemSelectionMajorMethod} — {@code "Item"} or {@code "Set"}</li>
 *   <li>{@link #itemSetSelectionWay} — e.g. {@code "Scan Item"}/{@code "Select Item"} (major
 *       method {@code "Item"}) or {@code "Select Set"} (major method {@code "Set"}); must name a
 *       constant of {@code ItemSelectionType}/{@code SetSelectionType} respectively</li>
 * </ul>
 */
@Getter
@Sheet(name ="ItemDetails")
public class ItemDetailsRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Item Selection Major method")
    private String itemSelectionMajorMethod;
    @Column(name = "Item/Set Selection ways")
    private String itemSetSelectionWay;
    @Column(name = "Item Type")
    private String itemType;

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getItemType() {
        return itemType;
    }

    public String getItemSetSelectionWay() {
        return itemSetSelectionWay;
    }

    public String getItemSelectionMajorMethod() {
        return itemSelectionMajorMethod;
    }
}
