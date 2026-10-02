package com.gsl.remote.pool;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.model.Credentials;
import com.gsl.utils.Log;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM-singleton pool of {@link SSHClient} instances, keyed by {@code host:port}.
 * Prevents connection storms when many test threads simultaneously need the same server.
 *
 * <p>Pool config (from FrameworkConfig):
 * <ul>
 *   <li>{@code remote.logs.ssh.pool.size} — max connections per host (default 5)</li>
 *   <li>{@code remote.logs.ssh.pool.max.wait.seconds} — borrow timeout (default 10s)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   SSHClient client = SshConnectionPool.getInstance().borrow(host, port, creds, ...);
 *   try {
 *       // use client
 *   } catch (TransportException e) {
 *       SshConnectionPool.getInstance().invalidate(host, port, client);
 *       client = null;
 *   } finally {
 *       if (client != null) SshConnectionPool.getInstance().returnClient(host, port, client);
 *   }
 * </pre>
 */
public final class SshConnectionPool {

    private static final class Holder {
        static final SshConnectionPool INSTANCE = new SshConnectionPool();
    }

    private final ConcurrentHashMap<String, GenericObjectPool<SSHClient>> pools =
            new ConcurrentHashMap<>();

    private SshConnectionPool() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ssh-pool-shutdown"));
    }

    public static SshConnectionPool getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Borrows a connected, authenticated {@link SSHClient}.
     * Creates a new pool for this {@code host:port} if one does not yet exist.
     *
     * @throws Exception if the pool wait times out or client creation fails
     */
    public SSHClient borrow(String host, int port, Credentials creds,
                            int connectTimeoutSeconds, int readTimeoutSeconds,
                            String hostKeyVerify) throws Exception {
        return getOrCreatePool(host, port, creds, connectTimeoutSeconds,
                readTimeoutSeconds, hostKeyVerify).borrowObject();
    }

    /** Returns a healthy client to its pool. Never throws. */
    public void returnClient(String host, int port, SSHClient client) {
        GenericObjectPool<SSHClient> pool = pools.get(poolKey(host, port));
        if (pool != null) {
            try { pool.returnObject(client); }
            catch (Exception ignored) {}
        }
    }

    /** Invalidates (discards) a client after a transport error. Never throws. */
    public void invalidate(String host, int port, SSHClient client) {
        GenericObjectPool<SSHClient> pool = pools.get(poolKey(host, port));
        if (pool != null) {
            try { pool.invalidateObject(client); }
            catch (Exception ignored) {}
        }
    }

    /** Closes and drains all pools. Called by the shutdown hook. */
    public void shutdown() {
        pools.values().forEach(pool -> {
            try { pool.close(); } catch (Exception ignored) {}
        });
        pools.clear();
        Log.debug("[SshConnectionPool] All SSH connection pools closed.");
    }

    // ── private ───────────────────────────────────────────────────────────────

    /** Returns the existing pool for {@code host:port}, creating and configuring one on first use. */
    private GenericObjectPool<SSHClient> getOrCreatePool(
            String host, int port, Credentials creds,
            int connectTimeoutSeconds, int readTimeoutSeconds,
            String hostKeyVerify) {

        return pools.computeIfAbsent(poolKey(host, port), k -> {
            GenericObjectPoolConfig<SSHClient> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(ConfigFactory.getRemoteLogConfig().remoteSshPoolSize());
            config.setBlockWhenExhausted(true);
            config.setMaxWaitMillis(
                    (long) ConfigFactory.getRemoteLogConfig().remoteSshPoolMaxWaitSeconds() * 1_000L);
            config.setTestOnBorrow(true);
            config.setTimeBetweenEvictionRunsMillis(30_000L);
            config.setMinEvictableIdleTimeMillis(60_000L);

            return new GenericObjectPool<>(
                    new SshClientFactory(host, port, creds,
                            connectTimeoutSeconds, readTimeoutSeconds, hostKeyVerify),
                    config);
        });
    }

    /** Builds the {@link #pools} map key for a host/port pair. */
    private static String poolKey(String host, int port) {
        return host + ":" + port;
    }

    // ── Inner factory ─────────────────────────────────────────────────────────

    /** Commons Pool2 factory that creates, validates, and destroys pooled {@link SSHClient}s for one host/port. */
    private static final class SshClientFactory extends BasePooledObjectFactory<SSHClient> {

        private final String      host;
        private final int         port;
        private final Credentials creds;
        private final int         connectTimeoutSeconds;
        private final int         readTimeoutSeconds;
        private final String      hostKeyVerify;

        SshClientFactory(String host, int port, Credentials creds,
                         int connectTimeoutSeconds, int readTimeoutSeconds,
                         String hostKeyVerify) {
            this.host                 = host;
            this.port                 = port;
            this.creds                = creds;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.readTimeoutSeconds    = readTimeoutSeconds;
            this.hostKeyVerify         = hostKeyVerify;
        }

        /** Creates, connects, and authenticates a brand-new {@link SSHClient} for this factory's host/port. */
        @Override
        public SSHClient create() throws Exception {
            SSHClient ssh = new SSHClient();
            applyHostKeyVerification(ssh, hostKeyVerify);
            ssh.setConnectTimeout(connectTimeoutSeconds * 1_000);
            ssh.setTimeout(readTimeoutSeconds * 1_000);
            ssh.connect(host, port);
            ssh.authPassword(creds.getUsername(), creds.getPassword());
            return ssh;
        }

        /** Wraps a raw {@link SSHClient} in the pool's tracking object. */
        @Override
        public PooledObject<SSHClient> wrap(SSHClient client) {
            return new DefaultPooledObject<>(client);
        }

        /** A pooled client is still usable when connected and authenticated (checked via {@code testOnBorrow}). */
        @Override
        public boolean validateObject(PooledObject<SSHClient> p) {
            SSHClient client = p.getObject();
            return client != null && client.isConnected() && client.isAuthenticated();
        }

        /** Disconnects a client being evicted or invalidated; swallows errors since it's already being discarded. */
        @Override
        public void destroyObject(PooledObject<SSHClient> p) {
            try { p.getObject().disconnect(); } catch (Exception ignored) {}
        }
    }

    // ── Shared host-key verification logic ────────────────────────────────────

    /**
     * Configures {@code ssh}'s host-key verifier per {@code mode}, shared by both the pooled
     * {@link SshClientFactory#create()} path and {@link com.gsl.remote.fetcher.SshLogFetcher}'s
     * unpooled path.
     *
     * @param mode {@code "STRICT"} (known_hosts, no auto-accept), {@code "DISABLED"} (accept any
     *             host key), or anything else (default) for accept-and-remember-new
     */
    public static void applyHostKeyVerification(SSHClient ssh, String mode) {
        switch (mode == null ? "ACCEPT_NEW" : mode.toUpperCase(Locale.ROOT)) {
            case "STRICT" -> {
                try {
                    ssh.loadKnownHosts();
                } catch (IOException e) {
                    Log.warn("[SshConnectionPool] Could not load known_hosts for STRICT mode; falling back to ACCEPT_NEW");
                    applyAcceptNew(ssh);
                }
            }
            case "DISABLED" -> ssh.addHostKeyVerifier(new PromiscuousVerifier());
            default -> applyAcceptNew(ssh);
        }
    }

    /** Accept-and-remember-new: verifies against (and appends to) {@code ~/.ssh/known_hosts}, creating it if absent. */
    private static void applyAcceptNew(SSHClient ssh) {
        try {
            File knownHosts = new File(System.getProperty("user.home"), ".ssh/known_hosts");
            if (!knownHosts.exists()) {
                knownHosts.getParentFile().mkdirs();
                knownHosts.createNewFile();
            }
            ssh.addHostKeyVerifier(new OpenSSHKnownHosts(knownHosts));
        } catch (IOException e) {
            Log.warn("[SshConnectionPool] Could not open known_hosts — using DISABLED verifier");
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }
    }
}
