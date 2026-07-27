package com.gsl.testdata.Finance.Setup.AccountGroup;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

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
