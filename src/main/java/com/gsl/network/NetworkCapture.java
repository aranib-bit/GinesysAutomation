package com.gsl.network;

import java.util.List;

/**
 * Strategy interface for capturing browser network traffic during a test session, selected per
 * driver by {@code NetworkServiceFactory} and bound to the current thread via
 * {@link NetworkCaptureManager} so each parallel test gets its own isolated capture.
 *
 * <p>Typical lifecycle: {@link #start()} right after the driver is created (and the instance is
 * stored in {@link NetworkCaptureManager}), {@link #getLogs()} read at failure/attachment time by
 * {@code NetworkLogAttachment}, {@link #stop()} at driver teardown. {@link #clear()} lets a caller
 * reset the buffer mid-session without tearing down the underlying capture.
 *
 * <ul>
 *   <li>{@link BiDiNetworkCapture} — Firefox, via the WebDriver BiDi Network module</li>
 *   <li>{@link CdpNetworkCapture} — Chrome/Edge, via Chrome DevTools Protocol</li>
 *   <li>{@link NoOpNetworkCapture} — capture disabled or driver supports neither protocol</li>
 * </ul>
 */
public interface NetworkCapture {

    /** Begins capturing network traffic for the current session. */
    void start();

    /** Stops capturing network traffic; whether this also tears down the underlying session/listeners is implementation-defined. */
    void stop();

    /** Discards any logs captured so far. */
    void clear();

    /** Returns the network logs captured so far. */
    List<NetworkLogRecord> getLogs();

    /** Short label identifying the capture protocol in use (e.g. {@code "CDP"}, {@code "BiDi"}, {@code "NONE"}), used in reports. */
    String protocolName();
}
