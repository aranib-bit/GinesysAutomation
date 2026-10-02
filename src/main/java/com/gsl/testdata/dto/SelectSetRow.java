package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;
import lombok.Getter;

/**
 * One row of the "SelectSet" sheet in the receipts workbook — the set-search detail rows joined
 * onto an {@link ItemBlock} by {@code MergedReceiptDataSupplier} when that block's {@link
 * ItemDetailsRow#getItemSelectionMajorMethod()} is {@code "Set"} and {@link
 * ItemDetailsRow#getItemSetSelectionWay()} is {@code "Select Set"}. {@link #setId} identifies the
 * item-set to select in the UI.
 */
@Getter
@Sheet(name="SelectSet")
public class SelectSetRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "SetID")
    private String setId;

}
