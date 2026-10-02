package com.gsl.testdata.Procurement;

/**
 * {@link ItemDetail} variant for a "Batch" line-item row (batch code + expiry date) — built by
 * {@link ItemDetailFactory#create} from an {@code "ItemType"="Batch"} row. See {@link ItemDetail}'s
 * Javadoc for this whole chain's orphaned/unconfirmed-live-caller status.
 */
public class BatchItemDetail implements ItemDetail {
    private String testCaseId;
    private String batchCode;
    private String expiryDate;
    private String itemSelectionMajorMethod;
    private String itemSetSelectionSelectionWays;

    /** Always {@code "Batch"} — this variant's {@link ItemDetail#getItemType()} discriminator. */
    @Override
    public String getItemType() {
        return "Batch";
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

    /** The batch code identifying which batch this line-item was received/scanned against. */
    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    /** The batch's expiry date, as read from the source row (unparsed string). */
    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setItemSelectionMajorMethod(String itemSelectionMajorMethod) {
        this.itemSelectionMajorMethod = itemSelectionMajorMethod;
    }

    public void setItemSetSelectionSelectionWays(String itemSetSelectionSelectionWays) {
        this.itemSetSelectionSelectionWays = itemSetSelectionSelectionWays;
    }
}
