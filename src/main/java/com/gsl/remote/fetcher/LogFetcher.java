package com.gsl.remote.fetcher;

import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.model.LogFetchRequest;
import com.gsl.remote.model.LogFetchResponse;
import com.gsl.remote.model.OSType;

/** Retrieves raw log lines from a remote server. One implementation per OS/protocol. */
public interface LogFetcher {

    /**
     * Fetches log entries from the remote server described in {@code request}.
     *
     * @throws RemoteCommandException when the remote command exits non-zero or times out
     *                                after exhausting all retries
     */
    LogFetchResponse fetch(LogFetchRequest request) throws RemoteCommandException;

    /** Returns true when this fetcher can handle the given OS type. */
    boolean supports(OSType osType);
}
