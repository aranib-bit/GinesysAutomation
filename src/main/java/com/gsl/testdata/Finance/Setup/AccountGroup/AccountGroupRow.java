package com.gsl.testdata.Finance.Setup.AccountGroup;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

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
