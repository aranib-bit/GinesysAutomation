package com.gsl.testdata.Procurement;

/**
 * {@link ItemDetail} variant for a plain "Item"-type line-item selected via the "Select Item"
 * (not "Scan Item") way — built by {@link ItemDetailFactory#create} when {@code "Item/Set
 * Selection ways"} is anything other than {@code "scanitem"} (contrast {@link ScanItemAdapter},
 * the sibling "Item" variant for the scan-item way). See {@link ItemDetail}'s Javadoc for this
 * whole chain's orphaned/unconfirmed-live-caller status.
 */
public class ItemItemDetail implements ItemDetail {
    private String testCaseId;
    private String itemSelection;
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

    /** The row's "Item Selection" column value — how this specific item was picked (distinct from {@link #getItemSelectionMajorMethod()}). */
    public String getItemSelection() {
        return itemSelection;
    }

    public void setItemSelection(String itemSelection) {
        this.itemSelection = itemSelection;
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
