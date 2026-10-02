package com.gsl.testdata.Procurement;

import java.util.Arrays;
import java.util.Map;

/**
 * Selects and builds the right {@link ItemDetail} variant from one raw row's column map, keyed by
 * its {@code "ItemType"} value ("Item" → {@link ItemItemDetail} or {@link ScanItemAdapter}
 * depending on {@code "Item/Set Selection ways"}; "Batch" → {@link BatchItemDetail}; "Serial" →
 * {@link SerialItemDetail}; "Regular" → {@link RegularItemDetail}). The row shape matches {@link
 * ItemDetailsData#toMap()}'s output.
 *
 * <p>{@code TODO-confirm:} {@link #create} has no confirmed caller anywhere in the codebase
 * (confirmed by repo-wide search) — see {@link ItemDetail}'s Javadoc for the full orphaned-chain
 * finding.
 */
public class ItemDetailFactory {

    /**
     * Builds the {@link ItemDetail} variant matching {@code row}'s {@code "ItemType"} entry — see
     * the class Javadoc for the type-to-variant mapping.
     *
     * @param row column-name-to-value map for one item-detail row (see {@link
     *            ItemDetailsData#toMap()})
     * @throws IllegalArgumentException if {@code "ItemType"} is missing or names none of
     *                                   Item/Batch/Serial/Regular
     */
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
