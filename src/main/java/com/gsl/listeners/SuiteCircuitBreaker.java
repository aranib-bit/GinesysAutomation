package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ExecutionConfig;
import com.gsl.reports.ExtentReport;
import com.gsl.utils.Log;
import org.testng.ISuite;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Suite-wide pass/fail tally that aborts a run once the failure rate crosses a configured
 * threshold, instead of burning through every remaining test in an already-broken environment.
 *
 * <p>{@link com.gsl.listeners.TestListener} calls {@link #recordPass()}/{@link #recordFail(ISuite)}
 * from {@code onTestSuccess}/{@code onTestFailure}; once {@link #evaluate(ISuite)} trips the
 * breaker, {@code TestListener.onTestStart()} throws a {@code SkipException} for every subsequent
 * test so the suite stops producing noise rather than running to completion. Counters are
 * process-wide ({@link AtomicInteger}/{@link AtomicBoolean}), so {@link #reset()} must be called by
 * {@link SuiteDataLoader#onStart(ISuite)} at the start of each fresh suite run.
 *
 * <p>Governed by {@link com.gsl.config.ExecutionConfig}'s {@code suiteAbortEnabled}/
 * {@code suiteAbortMinTestsBeforeCheck}/{@code suiteAbortCheckInterval}/
 * {@code suiteAbortOnFailurePercent} settings — see {@link #evaluate(ISuite)}.
 */
public final class SuiteCircuitBreaker {

    private static final AtomicInteger passed  = new AtomicInteger(0);
    private static final AtomicInteger failed  = new AtomicInteger(0);
    private static final AtomicBoolean tripped = new AtomicBoolean(false);

    private SuiteCircuitBreaker() {}

    /** Records one more passing test toward the suite tally. */
    public static void recordPass() {
        passed.incrementAndGet();
    }

    /** Records one more failing test toward the suite tally, then re-evaluates the trip condition. */
    public static void recordFail(ISuite suite) {
        failed.incrementAndGet();
        evaluate(suite);
    }

    /** @return true once the failure-rate threshold has tripped the breaker for this suite run. */
    public static boolean isTripped() {
        return tripped.get();
    }

    public static int getPassCount() { return passed.get(); }
    public static int getFailCount() { return failed.get(); }

    /** Resets all counters and the tripped flag — call once at the start of each fresh suite run. */
    public static void reset() {
        passed.set(0);
        failed.set(0);
        tripped.set(false);
        Log.info("[SuiteCircuitBreaker] Counters reset for fresh suite run.");
    }

    /**
     * Checks the current failure rate against {@link com.gsl.config.ExecutionConfig}'s configured
     * threshold (skipped entirely when {@code suiteAbortEnabled} is off, or until
     * {@code suiteAbortMinTestsBeforeCheck} tests have run, and then only every
     * {@code suiteAbortCheckInterval}-th test). Trips the breaker at most once — a second
     * threshold breach after the first trip is a no-op — and marks {@code suite}'s state failed so
     * TestNG's own reporting reflects the abort.
     */
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
