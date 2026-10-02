package com.gsl.network;

/**
 * Stores one NetworkCapture instance per thread.
 * Each parallel test thread gets its own capture bound to its own browser session.
 * Callers never receive null — get() returns a NoOpNetworkCapture when nothing is set.
 */
public final class NetworkCaptureManager {

    private static final ThreadLocal<NetworkCapture> HOLDER = new ThreadLocal<>();

    private NetworkCaptureManager() {}

    /** Binds {@code capture} to the current thread, replacing whatever was previously set. */
    public static void set(NetworkCapture capture)  { HOLDER.set(capture); }

    /** Returns the current thread's bound capture, or a {@link NoOpNetworkCapture} if none was set. */
    public static NetworkCapture get() {
        NetworkCapture c = HOLDER.get();
        return c != null ? c : new NoOpNetworkCapture();
    }

    /** Clears the current thread's binding, e.g. at driver teardown. */
    public static void remove() { HOLDER.remove(); }
}
