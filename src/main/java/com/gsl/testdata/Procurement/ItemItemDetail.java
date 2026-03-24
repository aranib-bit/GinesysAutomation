package com.gsl.testdata.Procurement;

public class ItemItemDetail implements ItemDetail {
    private String testCaseId;
    private String itemSelection;
    private String majorMethod;
    private String itemSetSelectionSelectionWays;

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
