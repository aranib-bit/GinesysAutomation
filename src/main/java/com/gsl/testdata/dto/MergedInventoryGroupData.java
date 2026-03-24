package com.gsl.testdata.dto;

import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group.Group;
import com.gsl.testdata.MasterTestData;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MergedInventoryGroupData {
    private String testCaseId;
    private Group group;
    private MasterTestData masterTestData;
}
