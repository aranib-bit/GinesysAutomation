package com.gsl.testdata.Procurement;

import java.util.List;

public class SerialItemDetail implements ItemDetail {
    private String testCaseId;
    private List<String> serialNumbers;
    private String itemSelectionMajorMethod;
    private String itemSetSelectionSelectionWays;

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
