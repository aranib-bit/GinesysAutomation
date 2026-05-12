package com.gsl.remote.model;

import lombok.Value;

/**
 * SSH/WinRM credentials. Source exclusively from environment variables;
 * never store plaintext passwords in config.properties or source code.
 *
 * Environment variables:
 *   REMOTE_LOG_SSH_USER / REMOTE_LOG_SSH_PASS
 *   REMOTE_LOG_WINRM_USER / REMOTE_LOG_WINRM_PASS
 */
@Value
public class Credentials {
    String username;
    String password;

    /** Reads SSH credentials from environment variables. Returns null if not set. */
    public static Credentials fromSshEnv() {
        String user = System.getenv("REMOTE_LOG_SSH_USER");
        String pass = System.getenv("REMOTE_LOG_SSH_PASS");
        if (user == null || pass == null) return null;
        return new Credentials(user, pass);
    }

    /** Reads WinRM credentials from environment variables. Returns null if not set. */
    public static Credentials fromWinRmEnv() {
        String user = System.getenv("REMOTE_LOG_WINRM_USER");
        String pass = System.getenv("REMOTE_LOG_WINRM_PASS");
        if (user == null || pass == null) return null;
        return new Credentials(user, pass);
    }
}
