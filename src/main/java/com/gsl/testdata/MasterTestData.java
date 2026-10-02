package com.gsl.testdata;


import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Setter added so a DataSupplier can stamp the real @TestCaseId-derived id onto an
// already-built row post-load (see MergedFinGeneralVoucherDataSupplier) — this class is
// otherwise populated once, at construction, from Excel/DB-sourced field values.
/**
 * Common Excel-row test-case metadata shared across data suppliers: test case id/name, an
 * expected page title, and loose {@code age}/{@code date}/{@code invocationcount} fields. Rarely
 * used standalone — it is the generic "which test case is this row for" half of a merged DTO,
 * paired with a domain-specific payload (e.g. {@link MergedReceiptData}, or the various
 * {@code Merged*Data} DTOs under {@code com.gsl.testdata.dto}) to form one full data-provider row.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MasterTestData {

    @Column(name = "testcasename")
    private String testcasename;
    @Column(name = "expectedTitle")
    private String expectedTitle;
    @Column(name = "age", converter = SafeIntegerConverter.class)
    private Integer age;
    @Column(name = "date")
    private String date;
    @Column(name = "TestCaseID")
    private String testcaseid;
    @Column(name= "InvocationCount",converter = SafeIntegerConverter.class)
    private Integer invocationcount;
}
