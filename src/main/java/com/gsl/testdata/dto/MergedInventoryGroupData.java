package com.gsl.testdata.dto;

import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group.Group;
import com.gsl.testdata.MasterTestData;
import lombok.Builder;
import lombok.Data;

/**
 * Test data for one Inventory Group hierarchy row, produced by {@code MergedInvGroupDataSupplier}
 * for Inventory Group creation tests (e.g. {@code CreateGroup#CreateDivision}).
 *
 * <p>Unlike the Account Group suppliers' single-invocation model, this DTO is emitted once per
 * matched {@link Group} row: the supplier joins the shared {@code MasterTestData} sheet against
 * the group-hierarchy sheet ({@code ConfigFactory.getExcelDataConfig().inventorygroupPath()},
 * "CreateGroup" sheet) on {@code TestCaseID}, and each match becomes its own test invocation.
 */
@Data
@Builder
public class MergedInventoryGroupData {
    private String testCaseId;
    private Group group;
    private MasterTestData masterTestData;
}
