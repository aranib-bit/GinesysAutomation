package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelApVoucherDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * DataSupplier router for AP Voucher tests — delegates to the configured data source:
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) → {@link ExcelApVoucherDataSource}</li>
 *   <li>{@code testdata.source=DB}              → {@link DbApVoucherDataSource}</li>
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
 *
 * <h3>DB mode filtering</h3>
 * Two orthogonal filters are read from TestNG XML parameters (IntelliJ direct-run path)
 * or system properties (Maven Surefire path):
 * <ul>
 *   <li>{@code apv.group} — execution-group gate (MANDATORY / REGRESSION_PHASE1 / PHASE2 / PHASE3)</li>
 *   <li>{@code apv.tag}   — tag-set gate, comma-separated (ADVANCE, ADVANCE_TDS, DOCUMENT, DOC_FIFO, …)</li>
 * </ul>
 * Combining both applies AND logic — a placeholder must pass both gates to be emitted.
 */
public class MergedFinApVoucherDataSupplier {

    // runInParallel=true is SAFE here only because of SessionPool (com.gsl.driver.SessionPool):
    // TestNG runs each @Test method's rows on a short-lived per-method thread pool, and under the
    // old ThreadLocal-bound session model those dying threads leaked their held users/browsers
    // (jstack-confirmed incident, run 20260708_113201). Sessions now live in the pool — each row
    // borrows one in WebTest.setUp and returns it in tearDown — so ephemeral row-threads hold
    // nothing between rows, and rows from ALL methods interleave across all pooled sessions.
    @DataSupplier(name = "FinanceApVoucherData", flatMap = true, runInParallel = true)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

        // Read apv.group and apv.tag from TestNG XML <parameter> — used as fallback when
        // the system property is absent (IntelliJ direct-run; Maven sets system props instead).
        String xmlGroupParam = context.getCurrentXmlTest().getParameter("apv.group");
        String xmlTagParam   = context.getCurrentXmlTest().getParameter("apv.tag");

        TestDataSource<MergedFinanceApVoucherData> loader =
                "DB".equalsIgnoreCase(source)
                        ? new DbApVoucherDataSource(xmlGroupParam, xmlTagParam)
                        : new ExcelApVoucherDataSource();

        try {
            List<MergedFinanceApVoucherData> scenarios = loader.load(method.getName());

            Log.info("[DataSupplier] Emitting {} AP Voucher scenario row(s) for '{}' — users assigned by pool at runtime.",
                    scenarios.size(), method.getName());

            // null credential → WebTest.setUp() acquires from UserPool (blocking)
            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
            throw se;
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            String msg = String.format(
                    "[DataSupplier] SKIPPED '%s' — could not load AP Voucher test data (source=%s).%n" +
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
