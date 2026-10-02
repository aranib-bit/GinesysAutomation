package com.gsl.testdata.Procurement;

import java.util.List;

/**
 * {@link ItemDetail} variant for a "Serial" line-item row (a list of serial numbers) — built by
 * {@link ItemDetailFactory#create} from an {@code "ItemType"="Serial"} row, which splits the
 * source {@code "Serial Nos"} column on commas. See {@link ItemDetail}'s Javadoc for this whole
 * chain's orphaned/unconfirmed-live-caller status.
 */
public class SerialItemDetail implements ItemDetail {
    private String testCaseId;
    private List<String> serialNumbers;
    private String itemSelectionMajorMethod;
    private String itemSetSelectionSelectionWays;

    /** Always {@code "Serial"} — this variant's {@link ItemDetail#getItemType()} discriminator. */
    @Override
    public String getItemType() {
        return "Serial";
    }

    @Override
    public String getTestCaseId() {
        return testCaseId;
    }

    @Override
    public String getItemSelectionMajorMethod() {
        return itemSelectionMajorMethod;
    }

    @Override
    public String getItemSetSelectionSelectionWays() {
        return itemSetSelectionSelectionWays;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public List<String> getSerialNumbers() {
        return serialNumbers;
    }

    public void setSerialNumbers(List<String> serialNumbers) {
        this.serialNumbers = serialNumbers;
    }

    public void setItemSelectionMajorMethod(String itemSelectionMajorMethod) {
        this.itemSelectionMajorMethod = itemSelectionMajorMethod;
    }

    public void setItemSetSelectionSelectionWays(String itemSetSelectionSelectionWays) {
        this.itemSetSelectionSelectionWays = itemSetSelectionSelectionWays;
    }
}
