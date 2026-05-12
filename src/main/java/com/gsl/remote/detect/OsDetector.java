package com.gsl.remote.detect;

import com.gsl.remote.model.Credentials;
import com.gsl.remote.model.OSType;
import com.gsl.utils.Log;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Auto-detects the OS type of a remote host by running {@code uname -s} via a short-lived
 * SSH probe. Results are cached per {@code host:port} so each host is probed at most once
 * per JVM lifetime.
 *
 * <p>Detection logic:
 * <ul>
 *   <li>SSH probe succeeds and stdout starts with "Linux" → {@link OSType#LINUX}</li>
 *   <li>Any exception (SSH not available, auth failure, timeout) → {@link OSType#WINDOWS}</li>
 *   <li>Credentials null → {@link OSType#WINDOWS} (probe skipped)</li>
 * </ul>
 *
 * <p>The probe uses {@code PromiscuousVerifier} intentionally — it is a read-only OS probe,
 * not a data transfer. The actual log fetch uses the configured host key verifier.
 */
public final class OsDetector {

    private static final ConcurrentHashMap<String, OSType> CACHE = new ConcurrentHashMap<>();
    private static final int PROBE_TIMEOUT_SECONDS = 5;

    private OsDetector() {}

    /**
     * Returns the detected OS for the given host, using cache when available.
     *
     * @param host    remote hostname or IP
     * @param sshPort SSH port to probe (usually 22)
     * @param creds   SSH credentials; if null, probe is skipped and WINDOWS is assumed
     * @return detected OSType, never null
     */
    public static OSType detect(String host, int sshPort, Credentials creds) {
        if (creds == null) {
            Log.debug("[OsDetector] No SSH credentials — assuming WINDOWS for host={}", host);
            return OSType.WINDOWS;
        }
        return CACHE.computeIfAbsent(cacheKey(host, sshPort),
                k -> probeViaSsh(host, sshPort, creds));
    }

    /** Removes the cached entry for a host — useful after a host OS change or rebuild. */
    public static void evict(String host, int sshPort) {
        CACHE.remove(cacheKey(host, sshPort));
    }

    /** Clears the entire cache. */
    public static void clearCache() {
        CACHE.clear();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private static String cacheKey(String host, int sshPort) {
        return host + ":" + sshPort;
    }

    private static OSType probeViaSsh(String host, int sshPort, Credentials creds) {
        Log.debug("[OsDetector] Probing OS via SSH uname. host={} port={}", host, sshPort);
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.setConnectTimeout(PROBE_TIMEOUT_SECONDS * 1_000);
            ssh.setTimeout(PROBE_TIMEOUT_SECONDS * 1_000);

            ssh.connect(host, sshPort);
            ssh.authPassword(creds.getUsername(), creds.getPassword());

            try (Session session = ssh.startSession();
                 Session.Command cmd = session.exec("uname -s")) {

                String stdout = IOUtils.toString(cmd.getInputStream(), StandardCharsets.UTF_8);
                cmd.join(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (stdout != null && stdout.trim().equalsIgnoreCase("Linux")) {
                    Log.debug("[OsDetector] Detected LINUX for host={}", host);
                    return OSType.LINUX;
                }
                Log.debug("[OsDetector] uname returned '{}' — treating as WINDOWS for host={}", stdout.trim(), host);
                return OSType.WINDOWS;
            }
        } catch (Exception e) {
            Log.debug("[OsDetector] SSH probe failed for host={} — assuming WINDOWS: {}", host, e.getMessage());
            return OSType.WINDOWS;
        }
    }
}
