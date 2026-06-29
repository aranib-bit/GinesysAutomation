package com.gsl.remote.model;

import com.gsl.utils.Log;
import lombok.Value;

/**
 * SSH/WinRM credentials.
 *
 * <p>Prefer environment variables (CI/CD safe). Config-property fallbacks exist for
 * local/dev runs — a WARN is logged when they are used so the team is nudged toward
 * proper env-var setup.
 *
 * <p>Environment variables (highest priority):
 *   REMOTE_LOG_SSH_USER / REMOTE_LOG_SSH_PASS
 *   REMOTE_LOG_WINRM_USER / REMOTE_LOG_WINRM_PASS
 */
@Value
public class Credentials {
    String username;
    String password;

    // ── env-var-only factories (used by OsDetector SSH probe — no config context) ──

    /** Reads SSH credentials from environment variables only. Returns null if not set. */
    public static Credentials fromSshEnv() {
        String user = System.getenv("REMOTE_LOG_SSH_USER");
        String pass = System.getenv("REMOTE_LOG_SSH_PASS");
        if (user == null || pass == null) return null;
        return new Credentials(user, pass);
    }

    /** Reads WinRM credentials from environment variables only. Returns null if not set. */
    public static Credentials fromWinRmEnv() {
        String user = System.getenv("REMOTE_LOG_WINRM_USER");
        String pass = System.getenv("REMOTE_LOG_WINRM_PASS");
        if (user == null || pass == null) return null;
        return new Credentials(user, pass);
    }

    // ── env-var + config fallback factories (used by RemoteLogCollector) ──────────

    /**
     * Resolves WinRM credentials: env vars take priority; falls back to the supplied
     * config values when env vars are absent. Returns null when neither source has data.
     */
    public static Credentials fromWinRmEnvOrConfig(String configUser, String configPass) {
        String user = System.getenv("REMOTE_LOG_WINRM_USER");
        String pass = System.getenv("REMOTE_LOG_WINRM_PASS");
        if (user != null && pass != null) return new Credentials(user, pass);
        if (configUser != null && !configUser.isBlank()
                && configPass != null && !configPass.isBlank()) {
            Log.warn("[Credentials] Using plaintext WinRM credentials from remote-log.properties"
                    + " — prefer env vars REMOTE_LOG_WINRM_USER / REMOTE_LOG_WINRM_PASS");
            return new Credentials(configUser, configPass);
        }
        return null;
    }

    /**
     * Resolves SSH credentials: env vars take priority; falls back to the supplied
     * config values when env vars are absent. Returns null when neither source has data.
     */
    public static Credentials fromSshEnvOrConfig(String configUser, String configPass) {
        String user = System.getenv("REMOTE_LOG_SSH_USER");
        String pass = System.getenv("REMOTE_LOG_SSH_PASS");
        if (user != null && pass != null) return new Credentials(user, pass);
        if (configUser != null && !configUser.isBlank()
                && configPass != null && !configPass.isBlank()) {
            Log.warn("[Credentials] Using plaintext SSH credentials from remote-log.properties"
                    + " — prefer env vars REMOTE_LOG_SSH_USER / REMOTE_LOG_SSH_PASS");
            return new Credentials(configUser, configPass);
        }
        return null;
    }
}
