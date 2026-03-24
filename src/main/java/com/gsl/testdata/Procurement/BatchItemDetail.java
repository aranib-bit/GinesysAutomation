package com.gsl.testdata.Procurement;

public class BatchItemDetail implements ItemDetail {
    private String testCaseId;
    private String batchCode;
    private String expiryDate;
    private String itemSelectionMajorMethod;
    private String itemSetSelectionSelectionWays;

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

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

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
