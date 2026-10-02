package com.gsl.testdata.Procurement;

/**
 * {@link ItemDetail} variant for an "Item"-type line-item selected via the "Scan Item" way — built
 * by {@link ItemDetailFactory#create} when {@code "Item/Set Selection ways"} equals {@code
 * "scanitem"} (contrast {@link ItemItemDetail}, the sibling "Item" variant for every other
 * selection way). Named "adapter" because it wraps the same {@code ItemType="Item"} row shape
 * into the {@link ItemDetail} contract without an {@code itemSelection} field of its own. See
 * {@link ItemDetail}'s Javadoc for this whole chain's orphaned/unconfirmed-live-caller status.
 */
public class ScanItemAdapter implements ItemDetail {
    private String testCaseId;
    private String majorMethod;
    private String itemSetSelectionSelectionWays;

    /** Always {@code "Item"} — this variant's {@link ItemDetail#getItemType()} discriminator. */
    @Override
    public String getItemType() {
        return "Item";
    }

    @Override
    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    @Override
    public String getItemSelectionMajorMethod() {
        return majorMethod;
    }

    public void setMajorMethod(String majorMethod) {
        this.majorMethod = majorMethod;
    }

    @Override
    public String getItemSetSelectionSelectionWays() {
        return itemSetSelectionSelectionWays;
    }

    public void setItemSetSelectionSelectionWays(String itemSetSelectionSelectionWays) {
        this.itemSetSelectionSelectionWays = itemSetSelectionSelectionWays;
    }
}
