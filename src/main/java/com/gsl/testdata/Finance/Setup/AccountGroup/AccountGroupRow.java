package com.gsl.testdata.Finance.Setup.AccountGroup;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * One row of the "AccountGroup" sheet — a straightforward Account Group creation scenario at a
 * given hierarchy {@code level}/{@code parentGroup}.
 *
 * <p>Read by {@code MergedFinAccGroupDataSupplier} for {@code CreateAccountGroup}. Distinct from
 * {@link AccountGroupLifecycleRow}, which covers the edit/delete lifecycle scenarios.
 */
@Getter
@Sheet(name = "AccountGroup")
public class AccountGroupRow {

    @Column(name = "Group")
    public String groupName;

    @Column(name = "ParentGroup")
    public String parentGroup;

    @Column(name = "Level", converter = SafeIntegerConverter.class)
    public int level;

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "TestSuite")
    public String testSuite;
}
