package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.SafeDateConverter;
import java.time.LocalDate;

import com.gsl.converter.SafeDoubleConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.util.Date;

@Getter
@Sheet(name = "Store Wise Multiple Price")
public class ItemStoreWiseMultiplePrice {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Column(name = "StoreName")
    public String storename;

    @Column(name = "Initial")
    public String storeinitial;

    @Column(name = "Effective Date",converter = SafeDateConverter.class)
    public LocalDate  effectiveDate;

    @Column(name = "RSP",converter = SafeDoubleConverter.class)
    public double rsp;

    @Column(name = "MRP",converter = SafeDoubleConverter.class)
    public double mrp;
}
