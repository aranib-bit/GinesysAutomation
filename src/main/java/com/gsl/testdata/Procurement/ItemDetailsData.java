package com.gsl.testdata.Procurement;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

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

