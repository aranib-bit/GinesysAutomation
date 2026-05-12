package com.gsl.diagnostics.section;

public enum ServerRole {
    APP("App Server",  "diagnostics.app.server", "remote.app.server"),
    DB ("DB Server",   "diagnostics.db.server",  "remote.db.server");

    /** Human-readable label used in section titles and log messages. */
    public final String label;

    /** Config prefix used by {@link ServerUtilSection} and {@link EventLogSection} (diagnostics.*). */
    public final String configPrefix;

    /** Config prefix used by {@link com.gsl.remote.RemoteLogCollector} (remote.*). */
    public final String remoteConfigPrefix;

    ServerRole(String label, String configPrefix, String remoteConfigPrefix) {
        this.label              = label;
        this.configPrefix       = configPrefix;
        this.remoteConfigPrefix = remoteConfigPrefix;
    }
}
