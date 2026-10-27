package com.gsl.config;

import com.gsl.database.DbAvailability;
import com.gsl.enums.TestDataSourceMode;
import com.gsl.enums.TestEnvironment;
import com.gsl.utils.Log;

import java.net.URI;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates module-scoped config at suite start. Fails fast on fatal misconfigurations
 * before any DB work or browser launch begins.
 *
 * Wired into SuiteDataLoader.onStart() after validateUserPoolAgainstThreadCount().
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * Confirms the loaded values really belong to the selected environment: no unfilled
     * {@code CHANGE_ME} placeholders or missing secrets, and the UI host, WebAPI host, DB host and
     * DB name match what {@link TestEnvironment} expects. A mismatch is the "UI on one environment,
     * data in another" failure, so it is fatal and spells out each wrong value.
     */
    private static void validateEnvironment(FrameworkConfig core, List<String> fatals) {
        TestEnvironment env = EnvResolver.environment();
        String where = "environment '" + env.folder() + "' (selected via " + EnvResolver.source() + ")";
        DataBaseConfig db = ConfigFactory.getDataBaseConfig();
        ApiConfig api = ConfigFactory.getApiConfig();

        requireFilled(fatals, where, "url", core.url());
        // appusername / apppassword are obsolete: logins come from UsersList.xlsx (UserPool).
        boolean dbAvailable = dbAvailableOrRecordFatal(core, fatals);
        if (dbAvailable) {
            requireFilled(fatals, where, "databasehost", db.databaseHost());
            requireFilled(fatals, where, "databasename", db.databasename());
            requireFilled(fatals, where, "databaseusername", db.databaseUsername());
            requireFilled(fatals, where, "databasepassword", db.databasePassword());
        } else if (!isBlank(db.databaseHost()) || !isBlank(db.databasename())) {
            Log.warn("[ConfigValidator] {}: database values are present but db.available=no — they are ignored", where);
        }
        try {
            if (!com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.UI)
                    && !com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.API)
                    && !com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.DB)) {
                Log.warn("[ConfigValidator] {}: UI, API and DB validation are all off — tests will only exercise the flows", where);
            }
        } catch (IllegalArgumentException e) {
            fatals.add(e.getMessage());
        }
        requireFilled(fatals, where, "webapi.baseuri", api.webApiBaseUri());
        requireFilled(fatals, where, "api.tenant.active", api.activeTenant());

        expectHost(fatals, where, "url", core.url(), env.expectedUiHost());
        expectHost(fatals, where, "webapi.baseuri", api.webApiBaseUri(), env.expectedUiHost());
        expectHost(fatals, where, "webapi.financeApiBaseUri", api.financeApiBaseUri(), env.expectedUiHost());
        if (dbAvailable) {
            expectEquals(fatals, where, "databasehost", db.databaseHost(), env.expectedDbHost());
            expectEquals(fatals, where, "databasename", db.databasename(), env.expectedDbName());
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    /** Parses {@code db.available}; an invalid value is reported by {@link #checkDataSourceRules} and treated as {@code yes} here so the other checks still run. */
    private static boolean dbAvailableOrRecordFatal(FrameworkConfig core, List<String> fatals) {
        try {
            return DbAvailability.parseFlag(core.dbAvailable());
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * The data-source / DB-availability rules, free of config reads so they can be unit-tested:
     * {@code testdata.source} must be EXCEL, DB or API; {@code db.available} must be yes/no;
     * {@code validation.nodb.mode} must be API or SKIP; and {@code testdata.source=DB} cannot be
     * combined with {@code db.available=no} (collecting data from the DB needs the DB).
     *
     * @return the fatal messages (empty when the combination is valid)
     */
    public static List<String> checkDataSourceRules(String testDataSource, String dbAvailableRaw, String noDbModeRaw) {
        List<String> fatals = new ArrayList<>();
        TestDataSourceMode mode = null;
        try {
            mode = TestDataSourceMode.from(testDataSource);
        } catch (IllegalArgumentException e) {
            fatals.add(e.getMessage());
        }
        Boolean dbAvailable = null;
        try {
            dbAvailable = DbAvailability.parseFlag(dbAvailableRaw);
        } catch (IllegalArgumentException e) {
            fatals.add(e.getMessage());
        }
        try {
            DbAvailability.parseNoDbMode(noDbModeRaw);
        } catch (IllegalArgumentException e) {
            fatals.add(e.getMessage());
        }
        if (mode == TestDataSourceMode.DB && Boolean.FALSE.equals(dbAvailable)) {
            fatals.add("testdata.source=DB cannot be combined with db.available=no — collecting scenario data from the "
                    + "database needs the database. Use testdata.source=EXCEL (or API) for an environment without DB access.");
        }
        return fatals;
    }

    private static void requireFilled(List<String> fatals, String where, String key, String value) {
        if (value == null || value.isBlank()) {
            fatals.add(where + ": '" + key + "' is not set — put it in env/" + EnvResolver.current()
                    + "/ (or, for passwords, in secrets.local.properties / an environment variable)");
        } else if (value.contains("CHANGE_ME")) {
            fatals.add(where + ": '" + key + "' is still the CHANGE_ME placeholder");
        }
    }

    private static void expectHost(List<String> fatals, String where, String key, String actualUrl, String expectedHost) {
        if (expectedHost == null || actualUrl == null || actualUrl.isBlank()) return;
        String host;
        try {
            host = URI.create(actualUrl.trim()).getHost();
        } catch (IllegalArgumentException e) {
            fatals.add(where + ": '" + key + "' is not a valid URL: " + actualUrl);
            return;
        }
        if (host == null || !host.equalsIgnoreCase(expectedHost)) {
            fatals.add(where + ": '" + key + "' points at host '" + host + "' but this environment expects '"
                    + expectedHost + "' (value: " + actualUrl + ")");
        }
    }

    private static void expectEquals(List<String> fatals, String where, String key, String actual, String expected) {
        if (expected == null || actual == null || actual.isBlank()) return;
        if (!actual.trim().equalsIgnoreCase(expected)) {
            fatals.add(where + ": '" + key + "' is '" + actual + "' but this environment expects '" + expected + "'");
        }
    }

    /**
     * Checks {@link FrameworkConfig}, {@link ExecutionConfig}, and {@link FinanceConfig} for
     * known-fatal misconfigurations and throws immediately if any are found; non-fatal issues
     * (e.g. an out-of-range percentage) are only logged as warnings via {@link Log#warn}.
     *
     * @throws IllegalStateException if one or more fatal config errors were found — the
     *     individual errors are logged before the exception is thrown
     */
    public static void validate() {
        List<String> fatals = new ArrayList<>();

        FrameworkConfig   core   = ConfigFactory.getConfig();
        ExecutionConfig   exec   = ConfigFactory.getExecutionConfig();
        FinanceConfig     fin    = ConfigFactory.getFinanceConfig();

        validateEnvironment(core, fatals);
        BrowserResolver.current(); // logs the resolved browser (and fails on an unknown value) at suite start

        // ── Core ──────────────────────────────────────────────────────────────
        String url = core.url();
        if (url == null || url.isBlank()) {
            fatals.add("url is blank — set 'url' in config.properties");
        }

        fatals.addAll(checkDataSourceRules(core.testDataSource(), core.dbAvailable(), core.validationNoDbMode()));

        // ── Execution ─────────────────────────────────────────────────────────
        if (exec.threadCount() < 1) {
            fatals.add("thread.count=" + exec.threadCount() + " must be >= 1");
        }

        if (exec.concurrentUserCount() < 1) {
            Log.warn("[ConfigValidator] concurrent.user.count={} — expected >= 1", exec.concurrentUserCount());
        }

        int pct = exec.suiteAbortOnFailurePercent();
        if (pct < 0 || pct > 100) {
            Log.warn("[ConfigValidator] suite.abort.on.failure.percent={} is outside 0–100", pct);
        }

        // ── Finance ───────────────────────────────────────────────────────────
        if (fin.extjsMinTimeout() > fin.extjsTimeout()) {
            Log.warn("[ConfigValidator] extjs.min.timeout ({}) > extjs.timeout ({}) — min will be ignored",
                    fin.extjsMinTimeout(), fin.extjsTimeout());
        }

        // ── Emit & throw ──────────────────────────────────────────────────────
        if (!fatals.isEmpty()) {
            fatals.forEach(msg -> Log.error("[ConfigValidator] FATAL: {}", msg));
            throw new IllegalStateException(
                    "[ConfigValidator] " + fatals.size() + " fatal config error(s) — see logs above.");
        }

        Log.info("[ConfigValidator] Configuration validated OK.");
    }
}
