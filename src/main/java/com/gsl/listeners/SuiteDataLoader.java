package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbIdQueue;
import com.gsl.database.MenuOperationKey;
import com.gsl.utils.Log;
import org.testng.ISuite;
import org.testng.ISuiteListener;

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
}
