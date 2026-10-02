package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Configuration for pulling and scanning remote app-server/DB-server log files over SSH or
 * WinRM, read from {@code remote-log.properties}. Backs the remote-log diagnostics under
 * {@code com.gsl.diagnostics}/{@code com.gsl.database} that fetch server-side log tails on
 * failure and grep them for {@link #remoteLogsKeywords()}. The generic {@code remote.logs.*}
 * keys are legacy/shared defaults; the {@code remote.app.server.*} and {@code
 * remote.db.server.*} sections let the app server and DB server be configured independently
 * (different host, credentials, transport, and log paths).
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/secrets.local.properties",
        "file:${user.dir}/src/test/resources/env/${env}/remote-log.properties",
        "file:${user.dir}/src/test/resources/config/remote-log.properties"
})
public interface RemoteLogConfig extends Config {

    // ─── Capture behaviour ────────────────────────────────────────────────────

    /** NEVER/FAILURE/ALWAYS — when remote logs are fetched. */
    @Key("remote.logs.capture.mode")
    @DefaultValue("NEVER")
    String remoteLogsCaptureMode();

    /** Max number of trailing log lines fetched per file. */
    @Key("remote.logs.max.lines")
    @DefaultValue("500")
    long remoteLogsMaxLines();

    /** Comma-separated app-server log file paths (legacy/shared default; see {@link #remoteAppServerLogPaths()}). */
    @Key("remote.logs.app.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteLogsAppLogPaths();

    /** Comma-separated DB-server log file paths (legacy/shared default; see {@link #remoteDbServerLogPaths()}). */
    @Key("remote.logs.db.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteLogsDbLogPaths();

    /** Comma-separated keywords a fetched log line must contain to be reported. */
    @Key("remote.logs.keywords")
    @DefaultValue("ERROR,WARN,Exception")
    String remoteLogsKeywords();

    /** Whether verbose debug logging of the remote-log-fetch process itself is enabled. */
    @Key("remote.logs.debug.enabled")
    @DefaultValue("false")
    boolean remoteLogsDebugEnabled();

    // ─── SSH settings ─────────────────────────────────────────────────────────

    /** Default SSH port for remote log fetches. */
    @Key("remote.logs.ssh.port")
    @DefaultValue("22")
    int remoteSshPort();

    /** SSH connect timeout, in seconds. */
    @Key("remote.logs.ssh.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteSshConnectTimeoutSeconds();

    /** SSH read timeout, in seconds. */
    @Key("remote.logs.ssh.read.timeout.seconds")
    @DefaultValue("30")
    int remoteSshReadTimeoutSeconds();

    /** Timeout for a single remote command execution over SSH, in seconds. */
    @Key("remote.logs.ssh.command.timeout.seconds")
    @DefaultValue("60")
    int remoteSshCommandTimeoutSeconds();

    /** Number of retry attempts for a failed SSH operation. */
    @Key("remote.logs.ssh.retry.count")
    @DefaultValue("3")
    int remoteSshRetryCount();

    /** SSH host-key verification policy (e.g. ACCEPT_NEW, STRICT). */
    @Key("remote.logs.ssh.host.key.verify")
    @DefaultValue("ACCEPT_NEW")
    String remoteSshHostKeyVerify();

    /** Size of the pooled SSH session pool. */
    @Key("remote.logs.ssh.pool.size")
    @DefaultValue("5")
    int remoteSshPoolSize();

    /** Max time a caller waits to borrow an SSH session from the pool, in seconds. */
    @Key("remote.logs.ssh.pool.max.wait.seconds")
    @DefaultValue("10")
    int remoteSshPoolMaxWaitSeconds();

    // ─── WinRM settings ───────────────────────────────────────────────────────

    /** WinRM connect timeout, in seconds. */
    @Key("remote.logs.winrm.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteLogsWinrmConnectTimeoutSeconds();

    /** Timeout for a single remote WinRM operation, in seconds. */
    @Key("remote.logs.winrm.operation.timeout.seconds")
    @DefaultValue("60")
    int remoteLogsWinrmOperationTimeoutSeconds();

    /** Max number of rotated log files considered when following a WinRM-fetched log. */
    @Key("remote.logs.winrm.rotated.max.files")
    @DefaultValue("3")
    int remoteLogsWinrmRotatedMaxFiles();

    /** UTC offset of the remote server's clock (e.g. "Z", "+05:30"), used to align log timestamps. */
    @Key("remote.logs.server.timezone.offset")
    @DefaultValue("Z")
    String remoteLogsServerTimezoneOffset();

    // ─── Application server ───────────────────────────────────────────────────

    /** App server hostname; blank disables app-server remote-log fetches. */
    @Key("remote.app.server.host")
    @DefaultValue("")
    String remoteAppServerHost();

    /** Comma-separated app-server log file paths. */
    @Key("remote.app.server.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteAppServerLogPaths();

    /** Path to a file listing app-server log paths (one per line), used instead of/alongside {@link #remoteAppServerLogPaths()} when non-blank. */
    @Key("remote.app.server.log.paths.file")
    @DefaultValue("")
    String remoteAppServerLogPathsFile();

    /** SSH port for the app server. */
    @Key("remote.app.server.ssh.port")
    @DefaultValue("22")
    int remoteAppServerSshPort();

    /** WinRM port for the app server. */
    @Key("remote.app.server.winrm.port")
    @DefaultValue("5985")
    int remoteAppServerWinrmPort();

    /** Whether WinRM connections to the app server use HTTPS. */
    @Key("remote.app.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteAppServerWinrmUseHttps();

    /** WinRM username for the app server. */
    @Key("remote.app.server.winrm.username")
    @DefaultValue("")
    String remoteAppServerWinrmUsername();

    /** WinRM password for the app server. */
    @Key("remote.app.server.winrm.password")
    @DefaultValue("")
    String remoteAppServerWinrmPassword();

    /** SSH username for the app server. */
    @Key("remote.app.server.ssh.username")
    @DefaultValue("")
    String remoteAppServerSshUsername();

    /** SSH password for the app server. */
    @Key("remote.app.server.ssh.password")
    @DefaultValue("")
    String remoteAppServerSshPassword();

    // ─── Database server ──────────────────────────────────────────────────────

    /** DB server hostname; blank disables DB-server remote-log fetches. */
    @Key("remote.db.server.host")
    @DefaultValue("")
    String remoteDbServerHost();

    /** Comma-separated DB-server log file paths. */
    @Key("remote.db.server.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteDbServerLogPaths();

    /** SSH port for the DB server. */
    @Key("remote.db.server.ssh.port")
    @DefaultValue("22")
    int remoteDbServerSshPort();

    /** WinRM port for the DB server. */
    @Key("remote.db.server.winrm.port")
    @DefaultValue("5985")
    int remoteDbServerWinrmPort();

    /** Whether WinRM connections to the DB server use HTTPS. */
    @Key("remote.db.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteDbServerWinrmUseHttps();

    /** WinRM username for the DB server. */
    @Key("remote.db.server.winrm.username")
    @DefaultValue("")
    String remoteDbServerWinrmUsername();

    /** WinRM password for the DB server. */
    @Key("remote.db.server.winrm.password")
    @DefaultValue("")
    String remoteDbServerWinrmPassword();

    /** SSH username for the DB server. */
    @Key("remote.db.server.ssh.username")
    @DefaultValue("")
    String remoteDbServerSshUsername();

    /** SSH password for the DB server. */
    @Key("remote.db.server.ssh.password")
    @DefaultValue("")
    String remoteDbServerSshPassword();
}
