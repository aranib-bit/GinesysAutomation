package com.gsl.network;

/**
 * Stores one NetworkCapture instance per thread.
 * Each parallel test thread gets its own capture bound to its own browser session.
 * Callers never receive null — get() returns a NoOpNetworkCapture when nothing is set.
 */
public final class NetworkCaptureManager {

    private static final ThreadLocal<NetworkCapture> HOLDER = new ThreadLocal<>();

    private NetworkCaptureManager() {}

    public static void set(NetworkCapture capture)  { HOLDER.set(capture); }

    public static NetworkCapture get() {
        NetworkCapture c = HOLDER.get();
        return c != null ? c : new NoOpNetworkCapture();
    }

    public static void remove() { HOLDER.remove(); }
}
