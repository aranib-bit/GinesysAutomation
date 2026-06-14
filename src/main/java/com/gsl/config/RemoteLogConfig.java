package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/remote-log.properties")
public interface RemoteLogConfig extends Config {

    // ─── Capture behaviour ────────────────────────────────────────────────────

    @Key("remote.logs.capture.mode")
    @DefaultValue("NEVER")
    String remoteLogsCaptureMode();

    @Key("remote.logs.max.lines")
    @DefaultValue("500")
    long remoteLogsMaxLines();

    @Key("remote.logs.app.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteLogsAppLogPaths();

    @Key("remote.logs.db.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteLogsDbLogPaths();

    @Key("remote.logs.keywords")
    @DefaultValue("ERROR,WARN,Exception")
    String remoteLogsKeywords();

    @Key("remote.logs.debug.enabled")
    @DefaultValue("false")
    boolean remoteLogsDebugEnabled();

    // ─── SSH settings ─────────────────────────────────────────────────────────

    @Key("remote.logs.ssh.port")
    @DefaultValue("22")
    int remoteSshPort();

    @Key("remote.logs.ssh.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteSshConnectTimeoutSeconds();

    @Key("remote.logs.ssh.read.timeout.seconds")
    @DefaultValue("30")
    int remoteSshReadTimeoutSeconds();

    @Key("remote.logs.ssh.command.timeout.seconds")
    @DefaultValue("60")
    int remoteSshCommandTimeoutSeconds();

    @Key("remote.logs.ssh.retry.count")
    @DefaultValue("3")
    int remoteSshRetryCount();

    @Key("remote.logs.ssh.host.key.verify")
    @DefaultValue("ACCEPT_NEW")
    String remoteSshHostKeyVerify();

    @Key("remote.logs.ssh.pool.size")
    @DefaultValue("5")
    int remoteSshPoolSize();

    @Key("remote.logs.ssh.pool.max.wait.seconds")
    @DefaultValue("10")
    int remoteSshPoolMaxWaitSeconds();

    // ─── WinRM settings ───────────────────────────────────────────────────────

    @Key("remote.logs.winrm.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteLogsWinrmConnectTimeoutSeconds();

    @Key("remote.logs.winrm.operation.timeout.seconds")
    @DefaultValue("60")
    int remoteLogsWinrmOperationTimeoutSeconds();

    @Key("remote.logs.winrm.rotated.max.files")
    @DefaultValue("3")
    int remoteLogsWinrmRotatedMaxFiles();

    @Key("remote.logs.server.timezone.offset")
    @DefaultValue("Z")
    String remoteLogsServerTimezoneOffset();

    // ─── Application server ───────────────────────────────────────────────────

    @Key("remote.app.server.host")
    @DefaultValue("")
    String remoteAppServerHost();

    @Key("remote.app.server.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteAppServerLogPaths();

    @Key("remote.app.server.log.paths.file")
    @DefaultValue("")
    String remoteAppServerLogPathsFile();

    @Key("remote.app.server.ssh.port")
    @DefaultValue("22")
    int remoteAppServerSshPort();

    @Key("remote.app.server.winrm.port")
    @DefaultValue("5985")
    int remoteAppServerWinrmPort();

    @Key("remote.app.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteAppServerWinrmUseHttps();

    @Key("remote.app.server.winrm.username")
    @DefaultValue("")
    String remoteAppServerWinrmUsername();

    @Key("remote.app.server.winrm.password")
    @DefaultValue("")
    String remoteAppServerWinrmPassword();

    @Key("remote.app.server.ssh.username")
    @DefaultValue("")
    String remoteAppServerSshUsername();

    @Key("remote.app.server.ssh.password")
    @DefaultValue("")
    String remoteAppServerSshPassword();

    // ─── Database server ──────────────────────────────────────────────────────

    @Key("remote.db.server.host")
    @DefaultValue("")
    String remoteDbServerHost();

    @Key("remote.db.server.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteDbServerLogPaths();

    @Key("remote.db.server.ssh.port")
    @DefaultValue("22")
    int remoteDbServerSshPort();

    @Key("remote.db.server.winrm.port")
    @DefaultValue("5985")
    int remoteDbServerWinrmPort();

    @Key("remote.db.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteDbServerWinrmUseHttps();

    @Key("remote.db.server.winrm.username")
    @DefaultValue("")
    String remoteDbServerWinrmUsername();

    @Key("remote.db.server.winrm.password")
    @DefaultValue("")
    String remoteDbServerWinrmPassword();

    @Key("remote.db.server.ssh.username")
    @DefaultValue("")
    String remoteDbServerSshUsername();

    @Key("remote.db.server.ssh.password")
    @DefaultValue("")
    String remoteDbServerSshPassword();
}
