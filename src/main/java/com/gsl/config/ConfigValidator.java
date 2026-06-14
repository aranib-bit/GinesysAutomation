package com.gsl.config;

import com.gsl.utils.Log;

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

    public static void validate() {
        List<String> fatals = new ArrayList<>();

        FrameworkConfig   core   = ConfigFactory.getConfig();
        ExecutionConfig   exec   = ConfigFactory.getExecutionConfig();
        FinanceConfig     fin    = ConfigFactory.getFinanceConfig();

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
