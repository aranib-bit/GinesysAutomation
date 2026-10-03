package com.gsl.config;

import com.gsl.enums.TestEnvironment;
import com.gsl.utils.Log;

import java.net.URI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates module-scoped config at suite start. Fails fast on fatal misconfigurations
 * before any DB work or browser launch begins.
 *
 * Wired into SuiteDataLoader.onStart() after validateUserPoolAgainstThreadCount().
 */
public final class ConfigValidator {

    private static final Set<String> VALID_DATA_SOURCES = Set.of("EXCEL", "DB");

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
        requireFilled(fatals, where, "appusername", core.username());
        requireFilled(fatals, where, "apppassword", core.password());
        requireFilled(fatals, where, "databasehost", db.databaseHost());
        requireFilled(fatals, where, "databasename", db.databasename());
        requireFilled(fatals, where, "databaseusername", db.databaseUsername());
        requireFilled(fatals, where, "databasepassword", db.databasePassword());
        requireFilled(fatals, where, "webapi.baseuri", api.webApiBaseUri());
        requireFilled(fatals, where, "api.tenant.active", api.activeTenant());

        expectHost(fatals, where, "url", core.url(), env.expectedUiHost());
        expectHost(fatals, where, "webapi.baseuri", api.webApiBaseUri(), env.expectedUiHost());
        expectHost(fatals, where, "webapi.financeApiBaseUri", api.financeApiBaseUri(), env.expectedUiHost());
        expectEquals(fatals, where, "databasehost", db.databaseHost(), env.expectedDbHost());
        expectEquals(fatals, where, "databasename", db.databasename(), env.expectedDbName());
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

        String source = core.testDataSource();
        if (source == null || !VALID_DATA_SOURCES.contains(source.toUpperCase())) {
            fatals.add("testdata.source='" + source + "' is invalid — expected EXCEL or DB");
        }

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
