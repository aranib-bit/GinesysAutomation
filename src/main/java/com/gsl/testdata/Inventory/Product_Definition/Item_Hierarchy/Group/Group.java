package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

@Getter
@Sheet(name= "CreateGroup")
public class Group {
    @Column(name = "Test Scenario")
    public String testScenario;
    @Column(name="Group Name")
    public String groupName;
    @Column(name ="Group Level",converter = SafeIntegerConverter.class)
    public int groupLevel;
    @Column(name="ParentGroup")
    public String parentGroup;
    @Column(name = "TestCaseID")
    public String testCaseId;
    @Column(name="ParentGroup1")
    public String parentGroup1;

}
