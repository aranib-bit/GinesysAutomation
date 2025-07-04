package com.tests;

import com.testdata.TestData;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DataSupplierTest {

    @Test(dataProvider = "getData", dataProviderClass = com.utils.dataprovider.DataProviderUtils.class)
    public void dataSupplierTest(TestData testData) {
        System.out.println(testData.username);
        System.out.println(testData.age);
        LocalDate date = LocalDate.parse(testData.StartDate, DateTimeFormatter.ofPattern("M/d/yy"));

        System.out.println(testData.StartDate);
    }


}
