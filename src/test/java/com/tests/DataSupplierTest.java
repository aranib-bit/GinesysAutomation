package com.tests;

import com.testdata.TestData;
import com.utils.dataprovider.DataProviderUtils;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.TestDataReader;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class DataSupplierTest {

    @Test(dataProvider = "getData",dataProviderClass = com.utils.dataprovider.DataProviderUtils.class)
    public void dataSupplierTest(TestData testData){
        System.out.println(testData.username);
        System.out.println(testData.age);
        LocalDate date = LocalDate.parse(testData.StartDate, DateTimeFormatter.ofPattern("M/d/yy"));

        System.out.println(testData.StartDate);
    }



}
