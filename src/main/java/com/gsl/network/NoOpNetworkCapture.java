package com.gsl.network;

import java.util.List;

/**
 * Null-object {@link NetworkCapture} implementation — one of several {@link NetworkCapture}
 * implementations, selected by {@code NetworkServiceFactory} when network capture is disabled
 * via config, or when the current driver supports neither CDP ({@link CdpNetworkCapture}) nor
 * BiDi ({@link BiDiNetworkCapture}). Every method is a no-op so callers never need a null check;
 * this is also the fallback {@link NetworkCaptureManager#get()} returns when nothing was set.
 */
public final class NoOpNetworkCapture implements NetworkCapture {
    @Override public void start()                      {}
    @Override public void stop()                       {}
    @Override public void clear()                      {}
    @Override public List<NetworkLogRecord> getLogs()  { return List.of(); }
    @Override public String protocolName()             { return "NONE"; }
}
