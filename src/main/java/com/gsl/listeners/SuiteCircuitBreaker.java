package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ExecutionConfig;
import com.gsl.reports.ExtentReport;
import com.gsl.utils.Log;
import org.testng.ISuite;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SuiteCircuitBreaker {

    private static final AtomicInteger passed  = new AtomicInteger(0);
    private static final AtomicInteger failed  = new AtomicInteger(0);
    private static final AtomicBoolean tripped = new AtomicBoolean(false);

    private SuiteCircuitBreaker() {}

    public static void recordPass() {
        passed.incrementAndGet();
    }

    public static void recordFail(ISuite suite) {
        failed.incrementAndGet();
        evaluate(suite);
    }

    public static boolean isTripped() {
        return tripped.get();
    }

    public static int getPassCount() { return passed.get(); }
    public static int getFailCount() { return failed.get(); }

    public static void reset() {
        passed.set(0);
        failed.set(0);
        tripped.set(false);
        Log.info("[SuiteCircuitBreaker] Counters reset for fresh suite run.");
    }

    private static void evaluate(ISuite suite) {
        ExecutionConfig cfg = ConfigFactory.getExecutionConfig();

        if (!cfg.suiteAbortEnabled()) return;

        int total = passed.get() + failed.get();
        if (total < cfg.suiteAbortMinTestsBeforeCheck()) return;
        if (total % cfg.suiteAbortCheckInterval() != 0) return;

        double failureRate = (double) failed.get() / total * 100.0;

        if (failureRate >= cfg.suiteAbortOnFailurePercent()) {
            if (!tripped.compareAndSet(false, true)) return; // already tripped

            String banner = String.format(
                "\n╔══════════════════════════════════════════════════════════════╗\n" +
                "║  SUITE CIRCUIT BREAKER TRIPPED                               ║\n" +
                "║  Failure rate: %.1f%% (%d/%d) exceeds threshold: %d%%         ║\n" +
                "║  Aborting suite to prevent noise. Fix the deployment.        ║\n" +
                "╚══════════════════════════════════════════════════════════════╝",
                failureRate, failed.get(), total, cfg.suiteAbortOnFailurePercent());

            Log.error(banner);
            ExtentReport.extent.setSystemInfo("Suite Circuit Breaker",
                String.format("TRIPPED — %.1f%% failure rate (%d/%d)", failureRate, failed.get(), total));

            suite.getSuiteState().failed();
            // Remaining tests are intercepted in TestListener.onTestStart() via SkipException.
        }
    }
}
