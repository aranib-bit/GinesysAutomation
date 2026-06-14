package com.gsl.network;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.NetworkExtentLogger;

import java.util.List;

/**
 * Reads captured network traffic from the current thread's NetworkCapture instance
 * and attaches it to the ExtentReport. Called from TestListener on failure (and
 * optionally on success if network.capture.failed.only=false).
 */
public final class NetworkLogAttachment {

    private NetworkLogAttachment() {}

    public static void attach() {
        if (!ConfigFactory.getNetworkConfig().enableNetworkLogs()) return;

        NetworkCapture  capture  = NetworkCaptureManager.get();
        List<NetworkLogRecord> logs = capture.getLogs();
        if (logs.isEmpty()) return;

        NetworkExtentLogger.logNetworkLogs(logs, capture.protocolName());
    }
}
