package com.gsl.testdata.Procurement;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;

@Sheet(name = "ScanItem")
public class ScanItem {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Item ID")
    private String itemId;
    @Column(name = "Scan Mode")
    private String scanMode;
    @Column(name = "Mode")
    private String mode;
    @Column(name = "Quantity", converter = SafeDoubleConverter.class)
    private Double quantity;


    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getScanMode() {
        return scanMode;
    }

    public void setScanMode(String scanMode) {
        this.scanMode = scanMode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

}

