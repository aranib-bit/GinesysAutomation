package com.gsl.testdata.Procurement;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixture-data counterpart of an "ItemDetails" Excel sheet — a single flattened row carrying
 * every {@link ItemDetail} variant's possible columns (item selection, batch code/expiry, serial
 * numbers) in one shape, keyed by {@code "Item Type"}. {@link #toMap()} converts a row into the
 * {@code Map<String, String>} shape {@link ItemDetailFactory#create} expects, to build the
 * concrete {@link ItemDetail} variant matching this row's type.
 *
 * <p>{@code TODO-confirm:} no confirmed caller of {@link #toMap()} anywhere in the codebase — see
 * {@link ItemDetail}'s Javadoc for the full orphaned-chain finding.
 */
@Getter
@Sheet(name = "ItemDetails")
public class ItemDetailsData {

    @Column(name = "Item Type")
    private String itemType;

    @Column(name = "TestCaseID")
    private String testCaseId;

    @Column(name = "Item Selection Major method")
    private String itemSelection;

    @Column(name = "Item/Set Selection ways")
    private String itemSetSelectionWays;

    @Column(name = "Batch Code")
    private String batchCode;

    @Column(name = "Expiry Date")
    private String expiryDate;

    @Column(name = "Serial Nos")
    private String serialNos;


//    public void setItemSetSelectionWays(String itemSetSelectionWays) {
//        this.itemSetSelectionWays = itemSetSelectionWays;
//    }

    /** Converts this row's non-null fields into the column-name-keyed map {@link ItemDetailFactory#create} expects. */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (itemType != null) map.put("ItemType", itemType);
        if (testCaseId != null) map.put("TestCaseID", testCaseId);
        if (itemSelection != null) map.put("Item Selection", itemSelection);
        if (itemSetSelectionWays != null) map.put("Item/Set Selection ways", itemSetSelectionWays);
        if (batchCode != null) map.put("Batch Code", batchCode);
        if (expiryDate != null) map.put("Expiry Date", expiryDate);
        if (serialNos != null) map.put("Serial Nos", serialNos);
        return map;
    }
}

