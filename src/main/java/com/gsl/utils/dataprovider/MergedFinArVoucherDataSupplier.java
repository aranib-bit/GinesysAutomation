package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDeleteDataSource;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherEditDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelArVoucherDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * DataSupplier router — delegates to the configured data source:
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) → {@link ExcelArVoucherDataSource}</li>
 *   <li>{@code testdata.source=DB}              → {@link DbArVoucherDataSource}</li>
 * </ul>
 *
 * <p><b>Parallelism model (both modes):</b> each row carries a {@code null} user credential.
 * {@code WebTest.setUp()} calls {@link com.gsl.testdata.UserPool#acquireSet1User} which
 * <em>blocks</em> until a user is free. This guarantees:
 * <ul>
 *   <li>One user = one browser at a time — the same username is never logged into two
 *       sessions simultaneously.</li>
 *   <li>Net concurrency is capped at the number of users in {@code UsersList.xlsx},
 *       regardless of the {@code thread-count} value in testng.xml.</li>
 *   <li>All scenarios execute — one row per scenario, not one row per user.</li>
 * </ul>
 * {@code WebTest.tearDown()} releases the user back to the pool so the next waiting
 * thread can acquire it immediately.
 */
public class MergedFinArVoucherDataSupplier {

    // runInParallel=true is SAFE only because of SessionPool — see the note in
    // MergedFinApVoucherDataSupplier: rows borrow a pooled logged-in session per row
    // (WebTest.setUp) and return it (tearDown), so TestNG's short-lived per-method row threads
    // hold nothing between rows and can never leak users/browsers by dying.
    /**
     * Resolves the configured {@link TestDataSource} (EXCEL, or one of the three DB-mode sources
     * switched on {@code method}'s name — Edit/Delete/default-Create), loads that source's
     * scenario rows, and emits one {@code Object[]} (null credential + row) per scenario. A
     * data-load failure (DB unreachable, bad config, etc.) is logged with diagnosis and converted
     * to a {@link org.testng.SkipException} rather than propagated, so the test is skipped
     * cleanly instead of erroring with an opaque provider-level stack trace.
     *
     * @param method  the TestNG-injected test method — its name selects the DB-mode data source
     *                and is passed through to {@link TestDataSource#load}
     * @param context supplies the TestNG XML {@code arv.group} parameter (DB-mode Create fallback)
     */
    @DataSupplier(name = "FinanceArVoucherData", flatMap = true, runInParallel = true)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

        // Read arv.group from TestNG XML <parameter> — used as fallback when the system
        // property is absent (IntelliJ direct-run; Maven path sets the system property instead).
        String xmlGroupParam = context.getCurrentXmlTest().getParameter("arv.group");

        TestDataSource<MergedFinanceArVoucherData> loader =
                "DB".equalsIgnoreCase(source)
                        ? switch (method.getName()) {
                            case "editArVoucher"   -> new DbArVoucherEditDataSource();
                            case "deleteArVoucher" -> new DbArVoucherDeleteDataSource();
                            default                -> new DbArVoucherDataSource(xmlGroupParam);
                          }
                        : new ExcelArVoucherDataSource();

        try {
            List<MergedFinanceArVoucherData> scenarios = loader.load(method.getName());

            Log.info("[DataSupplier] Emitting {} scenario row(s) for '{}' — users assigned by pool at runtime.",
                    scenarios.size(), method.getName());

            // null credential → WebTest.setUp() acquires from UserPool (blocking)
            // Pool guarantees: same user never in two browsers simultaneously
            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
            throw se;
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
