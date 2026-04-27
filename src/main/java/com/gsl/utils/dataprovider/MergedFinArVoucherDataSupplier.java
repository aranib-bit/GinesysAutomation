package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
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
        Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

        TestDataSource<MergedFinanceArVoucherData> loader =
                "DB".equalsIgnoreCase(source)
                        ? new DbArVoucherDataSource()
                        : new ExcelArVoucherDataSource();

        try {
            return StreamEx.of(loader.load(method.getName()));
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            String msg = String.format(
                    "[DataSupplier] SKIPPED '%s' — could not load test data (source=%s).%n" +
                    "  Immediate cause : %s%n" +
                    "  Root cause      : %s%n" +
                    "  Possible reasons:%n" +
                    "    • DB host is not reachable from this machine (check VPN / network)%n" +
                    "    • DB service is down or not listening on the configured port%n" +
                    "    • Wrong credentials or database name in databaseconfig.properties%n" +
                    "  Action          : Verify manually — ping the host, check DB service status,%n" +
                    "                    or switch testdata.source=EXCEL to run without DB.",
                    method.getName(), source, e.getMessage(), root.getMessage());
            Log.error(msg);
            throw new org.testng.SkipException(msg);
        }
    }
}
