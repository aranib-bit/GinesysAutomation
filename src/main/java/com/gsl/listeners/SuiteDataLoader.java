package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ConfigValidator;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbIdQueue;
import com.gsl.database.MenuOperationKey;
import com.gsl.testdata.UserCredentialLoader;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.List;

/**
 * TestNG suite listener that pre-loads DB test-data ID queues before any parallel
 * test thread starts.
 *
 * <h3>How queues are registered</h3>
 * At suite start, a single DB query fetches all voucher-type × operation combinations
 * from {@code mdmenu} / {@code mdmenuappoperation} / {@code mdappoperation}.
 * One {@link DbIdQueue} queue is registered per combination using a key built by
 * {@link MenuOperationKey#build} — so the {@code Modify → Edit} UI mapping is applied
 * automatically.
 *
 * <h3>Adding a new voucher type</h3>
 * Insert the new menu row under {@code FinanceVoucher} in the DB.  No code changes needed —
 * this listener picks it up on the next run.
 *
 * <h3>Activation</h3>
 * Only runs when {@code testdata.source=DB} in {@code config.properties}.
 *
 * <h3>Registration in testng.xml</h3>
 * <pre>
 *   &lt;listeners&gt;
 *     &lt;listener class-name="com.gsl.listeners.SuiteDataLoader"/&gt;
 *   &lt;/listeners&gt;
 * </pre>
 */
public class SuiteDataLoader implements ISuiteListener {

    /**
     * SQL that returns every (menuShortcode, opShortcode) pair for all Finance voucher
     * types that support Add / Modify / Delete.
     * FinanceBRS (Bank Reconciliation) is excluded — it is not a voucher test type.
     */
    private static final String MENU_OP_SQL =
            "SELECT m.sys_shortcode AS menu_shortcode, " +
            "       op.sys_shortcode AS op_shortcode " +
            "FROM   mdmenu m " +
            "JOIN   mdmenuappoperation mao ON mao.sys_mdmenushortcode = m.sys_shortcode " +
            "JOIN   mdappoperation op      ON op.id = mao.sys_mdappoperationid " +
            "WHERE  m.sys_mdmenuparentshortcode = 'FinanceVoucher' " +
            "AND    m.sys_shortcode            != 'FinanceBRS' " +
            "AND    op.sys_shortcode           IN ('Add', 'Modify', 'Delete') " +
            "ORDER  BY m.sys_displayorder, op.sys_displayorder";

    @Override
    public void onStart(ISuite suite) {
        SuiteCircuitBreaker.reset();
        Log.info("[SuiteDataLoader] Initializing UserPool from UsersList.xlsx...");
        try {
            UserPool.initialize(UserCredentialLoader.loadAll());
        } catch (Exception e) {
            Log.warn("[SuiteDataLoader] UserPool init failed: {}. Tests needing user pool will fail.", e.getMessage());
        }

        // ── Suite-start validation (Plan 1 rule 11.1-11.2 / Plan 2 rule 13.1) ──
        validateUserPoolAgainstThreadCount(suite);
        ConfigValidator.validate();

        String source = ConfigFactory.getConfig().testDataSource();
        if (!"DB".equalsIgnoreCase(source)) {
            Log.info("[SuiteDataLoader] testdata.source='{}' — skipping DB queue load.", source);
            return;
        }

        Log.info("[SuiteDataLoader] testdata.source=DB — querying menu operations from DB...");

        List<String[]> rows = DatabaseManager.getJdbi().withHandle(handle ->
                handle.createQuery(MENU_OP_SQL)
                      .map((rs, ctx) -> new String[]{
                              rs.getString("menu_shortcode"),
                              rs.getString("op_shortcode")
                      })
                      .list()
        );

        if (rows.isEmpty()) {
            Log.warn("[SuiteDataLoader] No voucher operations found in DB — no queues registered. " +
                     "Check mdmenu WHERE sys_mdmenuparentshortcode='FinanceVoucher'.");
            return;
        }

        for (String[] row : rows) {
            String menuShortcode = row[0];
            String opShortcode   = row[1];
            String key           = MenuOperationKey.build(menuShortcode, opShortcode);
            DbIdQueue.register(key, sqlFor(key));
            Log.info("[SuiteDataLoader] Registered key='{}' — {} ID(s) available.",
                    key, DbIdQueue.remaining(key));
        }

        Log.info("[SuiteDataLoader] Done — {} queue(s) registered.", rows.size());
    }

    /**
     * Returns the SQL that fetches eligible IDs for the given queue key.
     * Each case targets the actual DB table/column for that voucher + operation.
     * Add a case here when a new voucher type uses a different table or filter.
     */
    private static String sqlFor(String key) {
        return switch (key) {
            // AR Voucher — Edit and Delete: all eligible vouchers.
            // release_status='N' = never posted  → directly editable/deletable.
            // release_status='U' = unposted       → directly editable/deletable.
            // release_status='P' = posted          → test class handles unpost + fintag check.
            case "FinanceARVoucher.Edit",
                 "FinanceARVoucher.Delete" ->
                    "SELECT vchcode AS id FROM finvchmain" +
                    " WHERE vchtype = 'R'" +
                    " AND release_status IN ('N', 'U', 'P')";

            // AR Voucher — Add: handled by DbArVoucherDataSource (lazy-placeholder pattern),
            // not by DbIdQueue. Register an empty queue so the key exists but is never consumed.
            case "FinanceARVoucher.Add" ->
                    "SELECT vchcode AS id FROM finvchmain WHERE 1=0";

            // Future: AP, Contra, GV — add cases here as DataSources are built.
            default -> {
                Log.warn("[SuiteDataLoader] No SQL defined for key='{}' — registering empty queue.", key);
                yield "SELECT vchcode AS id FROM finvchmain WHERE 1=0";
            }
        };
    }

    @Override
    public void onFinish(ISuite suite) {
        // No-op — queues are GC'd with the JVM after the suite.
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    /**
     * Validates that the configured user counts are compatible with each {@code <test>}
     * block's {@code thread-count}.
     *
     * <ul>
     *   <li><b>UI users = 0</b>: ERROR — all WebDriver tests will skip immediately.</li>
     *   <li><b>thread-count > UI user count</b>: WARN — extra threads will block on
     *       {@code acquireSet1User()} until a user is released.  Parallelism is capped
     *       at user count.  This is safe but users should be aware.</li>
     *   <li><b>API users = 0</b>: INFO — API acquire is skipped in setUp; UI-only tests
     *       are unaffected.</li>
     * </ul>
     */
    private static void validateUserPoolAgainstThreadCount(ISuite suite) {
        int uiCount  = UserPool.initialCountOf(UserType.UI);
        int apiCount = UserPool.initialCountOf(UserType.API);

        // ── UI user count check ───────────────────────────────────────────────
        if (uiCount == 0) {
            Log.error("[UserPool] VALIDATION ERROR: 0 UI users loaded from UsersList.xlsx. " +
                      "All WebDriver tests will be skipped. " +
                      "Add rows with userType=UI and executionSet=SET1.");
        } else {
            Log.info("[UserPool] Validation — UI users available: {}", uiCount);
        }

        // ── API user count check ──────────────────────────────────────────────
        if (apiCount == 0) {
            Log.info("[UserPool] Validation — No API users configured. " +
                     "API user acquire will be skipped in setUp (UI-only tests unaffected).");
        } else {
            Log.info("[UserPool] Validation — API users available: {}", apiCount);
        }

        // ── Per-test thread-count check ───────────────────────────────────────
        XmlSuite xmlSuite = suite.getXmlSuite();
        int suiteThreadCount = xmlSuite.getThreadCount();

        for (XmlTest xmlTest : xmlSuite.getTests()) {
            // XmlTest.getThreadCount() returns the suite default when not overridden
            int testThreadCount = xmlTest.getThreadCount() > 0
                    ? xmlTest.getThreadCount()
                    : suiteThreadCount;

            if (uiCount > 0 && testThreadCount > uiCount) {
                Log.warn("[UserPool] VALIDATION WARN — <test name=\"{}\">: " +
                         "thread-count={} exceeds UI user count={}. " +
                         "Effective parallelism = {}. " +
                         "Extra {} thread(s) will queue at acquireSet1User() until a user is released. " +
                         "Increase UsersList.xlsx UI users to {} if full parallelism is required.",
                        xmlTest.getName(), testThreadCount, uiCount,
                        uiCount, (testThreadCount - uiCount), testThreadCount);
            } else if (uiCount > 0) {
                Log.info("[UserPool] Validation OK — <test name=\"{}\">: " +
                         "thread-count={} <= UI users={}. Full parallelism achievable.",
                        xmlTest.getName(), testThreadCount, uiCount);
            }

            // ── Backfill headroom check ───────────────────────────────────────
            // SessionPool holds one UI user per thread for the ENTIRE WEB test lifetime
            // (parked on the list page between rows). A DataAvailabilityGuard backfill fires
            // on that SAME thread and asks the SAME UI pool for one more credential. If every
            // seat is already held 1:1 (thread-count == UI user count, previously considered
            // "OK — full parallelism achievable" above), the backfill's acquire deadlocks
            // waiting for a spare that will never free up. This check is separate from — and
            // stricter than — the plain thread-count-vs-UI-count check above.
            if (uiCount > 0) {
                int backfillHeadroom = ConfigFactory.getApiConfig().backfillUiPoolHeadroom();
                if (testThreadCount + backfillHeadroom > uiCount) {
                    String msg = String.format(
                            "[UserPool] BACKFILL DEADLOCK RISK — <test name=\"%s\">: thread-count=%d + " +
                            "backfill headroom=%d exceeds UI user count=%d. A WEB test's data-shortfall " +
                            "backfill (DataAvailabilityGuard) acquires an EXTRA UI-pool credential on the SAME " +
                            "thread that already holds one for its browser session — if every UI user is " +
                            "already checked out 1:1 by concurrent threads, a backfill attempt deadlocks " +
                            "waiting for a spare that never frees up. Add %d more UI user row(s) to " +
                            "UsersList.xlsx, or reduce thread-count, or lower datafallback.userPool.backfillHeadroom.",
                            xmlTest.getName(), testThreadCount, backfillHeadroom, uiCount,
                            (testThreadCount + backfillHeadroom - uiCount));
                    if ("FAIL".equalsIgnoreCase(ConfigFactory.getApiConfig().backfillHeadroomValidationMode())) {
                        throw new IllegalStateException(msg);
                    }
                    Log.error(msg);
                } else {
                    Log.info("[UserPool] Validation OK — <test name=\"{}\">: thread-count={} + backfill headroom={} <= UI users={}. " +
                             "Backfill has spare pool capacity, no deadlock risk.",
                            xmlTest.getName(), testThreadCount, backfillHeadroom, uiCount);
                }
            }

            if (apiCount > 0 && testThreadCount > apiCount) {
                Log.warn("[UserPool] VALIDATION WARN — <test name=\"{}\">: " +
                         "thread-count={} exceeds API user count={}. " +
                         "API user acquire may block if all API users are busy.",
                        xmlTest.getName(), testThreadCount, apiCount);
            }
        }
    }
}
