package com.utils.dataprovider;

import com.testdata.TestData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.TestDataReader;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.lang.reflect.Method;

public class DataProviderUtils {
    @DataSupplier //Object[][] or Object []
    public StreamEx<TestData> getData(Method method) {
        return TestDataReader.use(XlsxReader.class).
                withTarget(TestData.class).withSource("testdata.xlsx").read()
                .filter(testData -> testData.testcasename.equalsIgnoreCase(method.getName()));
    }
}
