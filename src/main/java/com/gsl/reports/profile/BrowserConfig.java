package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the browser/wait-tuning values shown in the Extent Report's Runtime
 * Profile "Browser Configuration" section (built by {@code RuntimeProfileBuilder#buildBrowser},
 * rendered by {@code ReportConfigRenderer#renderBrowserSection}).
 *
 * <p>Despite sharing its simple name with {@link com.gsl.config.BrowserConfig}, this is NOT a 1:1
 * mirror of it: only {@code headless}/{@code incognito}/{@code acceptInsecureCerts} come from
 * {@link com.gsl.config.BrowserConfig}. The remaining fields are pulled from three other live
 * config sources at suite start — {@code FrameworkConfig} ({@code itemScanTimeout}),
 * {@code FinanceConfig} ({@code extjsTimeout}/{@code extjsMinTimeout}), {@code ExecutionConfig}
 * ({@code syncTimeoutSeconds}), and {@code WaitConfig} (the {@code clickOverlayRetryMax}/
 * {@code docSelector*} fields) — reduced here into one flat, Jackson-serializable DTO purely for
 * display; nothing in the framework reads config values back off this class.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrowserConfig {
    private boolean headless;
    private boolean incognito;
    private boolean acceptInsecureCerts;
    private long itemScanTimeout;
    private long extjsTimeout;
    private long extjsMinTimeout;
    private int clickOverlayRetryMax;
    private long syncTimeoutSeconds;
    private String docSelectorMode;
    private int docSelectorOverfetchMultiplier;
    private long docSelectorRetryBackoffMs;
    private int docSelectorDocumentCountDefault;
}
