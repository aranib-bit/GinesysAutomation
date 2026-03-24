package com.gsl.testdata.Procurement;

import java.util.Arrays;
import java.util.Map;

public class ItemDetailFactory {

    public static ItemDetail create(Map<String, String> row) {
        String type = row.get("ItemType");
        if ("Item".equalsIgnoreCase(type)) {
            String itemSetSelectionWays = row.get("Item/Set Selection ways");
            if ("scanitem".equalsIgnoreCase(itemSetSelectionWays)) {
                ScanItemAdapter item = new ScanItemAdapter();
                item.setTestCaseId(row.get("TestCaseID"));
                item.setMajorMethod(row.get("Major method"));
                item.setItemSetSelectionSelectionWays(row.get("Item/Set Selection ways"));
                return item;
            } else {
                ItemItemDetail item = new ItemItemDetail();
                item.setTestCaseId(row.get("TestCaseID"));
                item.setItemSelection(row.get("Item Selection"));
                item.setMajorMethod(row.get("Major method"));
                item.setItemSetSelectionSelectionWays(row.get("Item/Set Selection ways"));
                return item;
            }
        } else if ("Batch".equalsIgnoreCase(type)) {
            BatchItemDetail batch = new BatchItemDetail();
            batch.setTestCaseId(row.get("TestCaseID"));
            batch.setBatchCode(row.get("Batch Code"));
            batch.setExpiryDate(row.get("Expiry Date"));
            return batch;
        } else if ("Serial".equalsIgnoreCase(type)) {
            SerialItemDetail serial = new SerialItemDetail();
            serial.setTestCaseId(row.get("TestCaseID"));
            serial.setSerialNumbers(
                Arrays.asList(row.get("Serial Nos").split(","))  // assuming comma-separated
            );
            return serial;
        } else if ("Regular".equalsIgnoreCase(type)) {
            RegularItemDetail regular = new RegularItemDetail();
            regular.setTestCaseId(row.get("TestCaseID"));
            regular.setItemCode(row.get("Item Code"));
            regular.setDescription(row.get("Description"));
            regular.setQuantity(Double.parseDouble(row.get("Quantity")));
            regular.setUom(row.get("UOM"));
            regular.setRemarks(row.get("Remarks"));
            return regular;
        }
        throw new IllegalArgumentException("Unsupported Item Type: " + type);
    }
}
