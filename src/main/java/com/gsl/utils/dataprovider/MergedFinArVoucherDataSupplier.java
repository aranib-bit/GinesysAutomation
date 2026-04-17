package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelArVoucherDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;

import java.lang.reflect.Method;

/**
 * DataSupplier router — delegates to the configured data source:
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) → {@link ExcelArVoucherDataSource}</li>
 *   <li>{@code testdata.source=DB}              → {@link DbArVoucherDataSource}</li>
 * </ul>
 * Switch by changing {@code testdata.source} in {@code config.properties}.
 * All existing Excel-based tests are unaffected when the value is {@code EXCEL}.
 */
public class MergedFinArVoucherDataSupplier {

    @DataSupplier(name = "FinanceArVoucherData", flatMap = true)
    public StreamEx<MergedFinanceArVoucherData> dataSupplier(Method method) {
        String source = ConfigFactory.getConfig().testDataSource();
        System.out.println("[MergedFinArVoucherDataSupplier] testdata.source=" + source
                + ", method=" + method.getName());

        TestDataSource<MergedFinanceArVoucherData> loader =
                "DB".equalsIgnoreCase(source)
                        ? new DbArVoucherDataSource()
                        : new ExcelArVoucherDataSource();

        return StreamEx.of(loader.load(method.getName()));
    }
}
