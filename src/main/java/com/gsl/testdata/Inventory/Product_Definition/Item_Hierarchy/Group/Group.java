package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group;

import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * Fixture-data counterpart of the "CreateGroup" Excel sheet ({@code
 * ConfigFactory.getExcelDataConfig().inventorygroupPath()}) — one row per Item-Group-hierarchy
 * node to create (Division/Section/Department/... depending on {@link #groupLevel}), joined to
 * its {@code MasterTestData} row on {@link #testCaseId} by {@code
 * com.gsl.utils.dataprovider.MergedInvGroupDataSupplier} into a {@code MergedInventoryGroupData}
 * invocation. Unlike the Item hierarchy sheets (see {@code
 * com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item}), this is a standalone
 * sheet, not merged with sibling sheets of its own.
 */
@Getter
@Sheet(name= "CreateGroup")
public class Group {
    @Column(name = "Test Scenario")
    public String testScenario;
    @Column(name="Group Name")
    public String groupName;
    /** Depth of this node in the group hierarchy (e.g. Division=1, Section=2, ...). */
    @Column(name ="Group Level",converter = SafeIntegerConverter.class)
    public int groupLevel;
    @Column(name="ParentGroup")
    public String parentGroup;
    @Column(name = "TestCaseID")
    public String testCaseId;
    /** Second parent-name column alongside {@link #parentGroup}; {@code TODO-confirm:} exact
     *  semantics (e.g. a second ancestor level vs. an alternate/fallback parent) not established
     *  from callers — {@code MergedInvGroupDataSupplier} passes it through unchanged. */
    @Column(name="ParentGroup1")
    public String parentGroup1;

}
