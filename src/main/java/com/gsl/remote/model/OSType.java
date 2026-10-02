package com.gsl.remote.model;

/**
 * Operating system of a remote log-collection target, as determined by
 * {@link com.gsl.remote.detect.OsDetector} and used to pick between
 * {@link com.gsl.remote.fetcher.SshLogFetcher} and {@link com.gsl.remote.fetcher.WinRmLogFetcher}.
 */
public enum OSType {
    /** Remote host is Windows; logs are fetched over WinRM. */
    WINDOWS,
    /** Remote host is Linux; logs are fetched over SSH. */
    LINUX
}
