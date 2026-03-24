package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;
import lombok.Getter;

@Getter
@Sheet(name ="ItemDetails")
public class ItemDetailsRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Item Selection Major method")
    private String itemSelectionMajorMethod;
    @Column(name = "Item/Set Selection ways")
    private String itemSetSelectionWay;
    @Column(name = "Item Type")
    private String itemType;

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getItemType() {
        return itemType;
    }

    public String getItemSetSelectionWay() {
        return itemSetSelectionWay;
    }

    public String getItemSelectionMajorMethod() {
        return itemSelectionMajorMethod;
    }
}
