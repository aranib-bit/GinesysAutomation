package com.gsl.testdata.Finance.Setup.AccountGroup;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * One row of the "AccountGroupLifecycle" sheet — hand-crafted Account Group naming-convention
 * and lifecycle (create, optional edit, optional delete) scenarios, at a given hierarchy
 * {@code level}/{@code parentGroup}.
 *
 * <p>Excel-only test data (no DB-sourcing counterpart), read by
 * {@code MergedAccountGroupLifecycleDataSupplier}. Distinct from the plain {@link AccountGroupRow}
 * used for straightforward Account Group creation.
 */
@Getter
@Sheet(name = "AccountGroupLifecycle")
public class AccountGroupLifecycleRow {

    @Column(name = "Group")
    public String groupName;

    @Column(name = "ParentGroup")
    public String parentGroup;

    @Column(name = "Level", converter = SafeIntegerConverter.class)
    public int level;

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "NamingCategory")
    public String namingCategory;

    @Column(name = "ExpectedOutcome")
    public String expectedOutcome;

    @Column(name = "EditAfterCreate")
    public String editAfterCreate;

    @Column(name = "NewNameIfEdited")
    public String newNameIfEdited;

    @Column(name = "DeleteAfterCreate")
    public String deleteAfterCreate;
}
