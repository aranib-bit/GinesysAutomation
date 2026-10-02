package com.gsl.testdata.Finance.Setup.AccountClass;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * One row of the "AccountClassLifecycle" sheet — hand-crafted Account Class naming-convention
 * and lifecycle (create, optional edit, optional duplicate-variant) scenarios.
 *
 * <p>Excel-only test data (no DB-sourcing counterpart), read by
 * {@code MergedAccountClassLifecycleDataSupplier}.
 */
@Getter
@Sheet(name = "AccountClassLifecycle")
public class AccountClassLifecycleRow {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "ClassName")
    public String className;

    @Column(name = "ClassType")
    public String classType;

    @Column(name = "NamingCategory")
    public String namingCategory;

    @Column(name = "ExpectedOutcome")
    public String expectedOutcome;

    @Column(name = "EditAfterCreate")
    public String editAfterCreate;

    @Column(name = "NewClassNameIfEdited")
    public String newClassNameIfEdited;

    @Column(name = "NewClassTypeIfEdited")
    public String newClassTypeIfEdited;

    @Column(name = "DuplicateVariantAfterCreate")
    public String duplicateVariantAfterCreate;
}
