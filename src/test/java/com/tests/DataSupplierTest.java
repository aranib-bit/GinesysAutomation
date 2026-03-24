package com.tests;

import com.gsl.testdata.MergedReceiptData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.MergedReceiptDataSupplier;
import org.testng.annotations.Test;

public class DataSupplierTest {

    @Test(dataProvider = "getData", dataProviderClass = MergedReceiptDataSupplier.class)
    public void dataSupplierTest(MasterTestData masterTestData) {
        System.out.println(masterTestData.username);
        System.out.println(masterTestData.age);
//        LocalDate date = LocalDate.parse(testData.date, DateTimeFormatter.ofPattern("M/d/yy"));
//
//        System.out.println(testData.date);
    }

    @Test(dataProvider = "receiptData", dataProviderClass = MergedReceiptDataSupplier.class)
    public void receiptDataTest(MergedReceiptData mergedReceiptData) {
        System.out.println(mergedReceiptData.getMasterTestData().getTestcasename());
        System.out.println(mergedReceiptData.getReceiptHeader().getVendorName());
        System.out.println(mergedReceiptData.getScanItems().get(0).getScanItem().getItemId());
    }


}
