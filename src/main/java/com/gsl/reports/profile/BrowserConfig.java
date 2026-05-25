package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

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
