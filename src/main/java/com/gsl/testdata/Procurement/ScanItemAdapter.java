package com.gsl.testdata.Procurement;

public class ScanItemAdapter implements ItemDetail {
    private String testCaseId;
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
