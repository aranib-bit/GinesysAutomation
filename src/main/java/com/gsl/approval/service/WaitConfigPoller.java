package com.gsl.approval.service;

import com.gsl.config.ConfigFactory;

import java.util.Optional;
import java.util.function.Supplier;

/** {@link ApprovalPoller} timed by {@code WaitConfig}: {@code polling.millis} between checks, {@code approval.poll.timeout.seconds} in all. */
public class WaitConfigPoller implements ApprovalPoller {

    @Override
    public <T> Optional<T> until(Supplier<Optional<T>> check) {
        var wait = ConfigFactory.getWaitConfig();
        long deadline = System.nanoTime() + wait.approvalPollTimeoutSeconds() * 1_000_000_000L;
        while (true) {
            Optional<T> result = check.get();
            if (result.isPresent() || System.nanoTime() >= deadline) {
                return result;
            }
            try {
                Thread.sleep(wait.pollingMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return check.get();
            }
        }
    }
}
