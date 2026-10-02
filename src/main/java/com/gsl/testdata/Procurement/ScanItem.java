package com.gsl.testdata.Procurement;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;

/**
 * Fixture-data counterpart of a "ScanItem" Excel sheet — one scanned item (item ID, scan mode,
 * mode, quantity), paired with its {@link ItemDetail} lines inside a {@link ScanItemWithDetails}.
 *
 * <p>{@code TODO-confirm:} no confirmed {@code XlsxReader.withTarget(ScanItem.class)} call
 * anywhere in the codebase, so this sheet is never actually read — see {@link ItemDetail}'s
 * Javadoc for the full orphaned-chain finding this class is part of.
 */
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

