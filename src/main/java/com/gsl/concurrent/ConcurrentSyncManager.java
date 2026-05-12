package com.gsl.concurrent;

import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Factory and await helper for CyclicBarrier synchronisation points used in SET-2 tests.
 * Each barrier is identified by a human-readable name used in log output.
 */
public final class ConcurrentSyncManager {

    private ConcurrentSyncManager() {}

    public static CyclicBarrier createBarrier(int parties, String name) {
        return new CyclicBarrier(parties, () ->
                Log.info("[ConcurrentSync] Barrier '{}' released ({} threads arrived).", name, parties));
    }

    public static void await(CyclicBarrier barrier, String name) {
        long timeout = ConfigFactory.getConfig().syncTimeoutSeconds();
        try {
            barrier.await(timeout, TimeUnit.SECONDS);
        } catch (BrokenBarrierException e) {
            Log.error("[ConcurrentSync] Barrier '{}' is broken — another thread failed.", name);
            throw new RuntimeException("Barrier broken: " + name, e);
        } catch (TimeoutException e) {
            barrier.reset();
            throw new RuntimeException(
                    "Barrier timeout (" + timeout + "s) at '" + name + "' — check thread-count matches concurrent.user.count.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted at barrier: " + name, e);
        }
    }
}
