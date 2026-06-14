package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.RetryHistoryBuffer;
import com.gsl.utils.Log;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * TestNG {@link IRetryAnalyzer} that retries failed tests up to {@code retry.max.count} times.
 *
 * <p>Wired automatically to every {@code @Test} method by {@link RetryAnnotationTransformer}
 * when {@code retry.max.count > 0}.  No test class changes are required.
 *
 * <p>Each retry attempt is recorded to {@link RetryHistoryBuffer}, which drives
 * {@link com.gsl.diagnostics.section.FlakySummarySection} in the diagnostic bundle.
 *
 * <p>A {@link ThreadLocal} attempt counter is used defensively: TestNG creates one
 * {@code IRetryAnalyzer} instance per {@code @Test} method per invocation under the
 * annotation-transformer path, but the ThreadLocal ensures correctness even if an
 * instance is reused across parallel threads in future TestNG versions.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private final int maxRetries = ConfigFactory.getExecutionConfig().retryMaxCount();

    /** Per-thread attempt counter — reset to 0 after the last retry to handle re-use. */
    private final ThreadLocal<Integer> attempt = ThreadLocal.withInitial(() -> 0);

    @Override
    public boolean retry(ITestResult result) {
        int current = attempt.get();
        if (current < maxRetries) {
            int nextAttempt = current + 1;
            attempt.set(nextAttempt);
            RetryHistoryBuffer.record(nextAttempt, result.getThrowable());
            Log.warn("[RetryAnalyzer] Retrying '{}' — attempt {}/{} — reason: {}",
                    result.getMethod().getMethodName(),
                    nextAttempt,
                    maxRetries,
                    result.getThrowable() != null
                            ? result.getThrowable().getClass().getSimpleName()
                            : "unknown");
            return true;
        }
        attempt.set(0);  // reset for potential re-use on a new test method
        return false;
    }
}
