package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;

/**
 * One row of the "Scan Set" sheet in the receipts workbook — intended as the set-scan analogue of
 * {@link ScanItemRow} (major method {@code "Set"}, selection way {@code "Scan Set"}).
 * {@code TODO-confirm:} {@code MergedReceiptDataSupplier} currently only wires up {@code "Select
 * Set"} for major method {@code "Set"} ({@link SelectSetRow}) — no code path reads this sheet or
 * populates a {@code scanSets} list on {@link ItemBlock}, so this row type appears unused/not yet
 * wired in rather than dead by design.
 */
@Data
@Sheet(name="Scan Set")
public class ScanSetRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Scan Set Detail")
    private String scanDetail;
}
