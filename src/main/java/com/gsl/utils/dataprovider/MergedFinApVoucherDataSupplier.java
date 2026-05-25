package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.finance.ExcelApVoucherDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * DataSupplier for AP Voucher tests.
 *
 * <p>Routes all methods to {@link ExcelApVoucherDataSource} (Excel-only mode).
 * MasterTestData {@code testcasename} routes rows to the correct method:
 * <ul>
 *   <li>{@code createApVoucherAdvancePayment} — On Account = Yes rows</li>
 *   <li>{@code createApVoucherDocumentPayment} — On Account = No rows</li>
 * </ul>
 *
 * <p>Parallelism: each row carries a {@code null} user credential.
 * {@code WebTest.setUp()} acquires a user from {@link com.gsl.testdata.UserPool}
 * (blocking), guaranteeing one user per browser session at a time.
 */
public class MergedFinApVoucherDataSupplier {

    @DataSupplier(name = "FinanceApVoucherData", flatMap = true, runInParallel = true)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

        try {
            List<MergedFinanceApVoucherData> scenarios =
                    new ExcelApVoucherDataSource().load(method.getName());

            Log.info("[DataSupplier] Emitting {} scenario row(s) for '{}' — users assigned by pool at runtime.",
                    scenarios.size(), method.getName());

            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
            throw se;
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            String msg = String.format(
                    "[DataSupplier] SKIPPED '%s' — could not load AP Voucher test data.%n" +
                    "  Immediate cause : %s%n" +
                    "  Root cause      : %s%n" +
                    "  Possible reasons:%n" +
                    "    • DB host is not reachable from this machine (check VPN / network)%n" +
                    "    • DB service is down or not listening on the configured port%n" +
                    "    • Wrong credentials or database name in databaseconfig.properties%n" +
                    "  Action          : Verify manually — ping the host, check DB service status.",
                    method.getName(), e.getMessage(), root.getMessage());
            Log.error(msg);
            throw new org.testng.SkipException(msg);
        }
    }
}
