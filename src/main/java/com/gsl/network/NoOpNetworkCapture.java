package com.gsl.network;

import java.util.List;

public final class NoOpNetworkCapture implements NetworkCapture {
    @Override public void start()                      {}
    @Override public void stop()                       {}
    @Override public void clear()                      {}
    @Override public List<NetworkLogRecord> getLogs()  { return List.of(); }
    @Override public String protocolName()             { return "NONE"; }
}
