package com.gsl.network;

import java.util.List;

public interface NetworkCapture {
    void start();
    void stop();
    void clear();
    List<NetworkLogRecord> getLogs();
    String protocolName();
}
