package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/wait.properties")
public interface WaitConfig extends Config {

    @Key("polling.millis")
    @DefaultValue("200")
    long pollingMillis();

    @Key("pageload.timeout.seconds")
    @DefaultValue("30")
    long pageLoadTimeoutSeconds();

    @Key("componentready.timeout.seconds")
    @DefaultValue("20")
    long componentReadyTimeoutSeconds();

    @Key("interaction.wait.seconds")
    @DefaultValue("10")
    long interactionWaitSeconds();

    @Key("short.wait.seconds")
    @DefaultValue("3")
    long shortWaitSeconds();

    @Key("popup.wait.seconds")
    @DefaultValue("30")
    long popupWaitSeconds();

    @Key("warning.msg.max.retries")
    @DefaultValue("2")
    int warningMsgMaxRetries();

    @Key("selectdoc.grid.load.buffer.ms")
    @DefaultValue("200")
    int selectDocGridLoadBufferMs();

    @Key("selectdoc.jump.render.ms")
    @DefaultValue("500")
    int selectDocJumpRenderMs();

    @Key("selectdoc.scroll.safety.cap")
    @DefaultValue("1000")
    int selectDocScrollSafetyCap();

    @Key("selectdoc.scroll.step.wait.ms")
    @DefaultValue("400")
    int selectDocScrollStepWaitMs();

    /**
     * Extra confirmation wait applied ONLY to a tick that already looks like end-of-list
     * (atBottom + !storeLoading + no progress vs. the previous read) — gives a slow
     * BufferedStore async page-fetch on a live/staging server real time to land and
     * re-render before a "no progress" reading is trusted. Independent of
     * {@link #selectDocScrollStepWaitMs()}, which stays fast for the common still-scrolling
     * path — this wait is only ever paid on a genuine end-of-list candidate tick.
     */
    @Key("selectdoc.scroll.noprogress.confirm.wait.ms")
    @DefaultValue("1200")
    int selectDocScrollNoProgressConfirmWaitMs();

    /**
     * Number of INDEPENDENTLY re-confirmed no-progress ticks required before declaring
     * genuine end-of-list (each counted tick already carries its own
     * {@link #selectDocScrollNoProgressConfirmWaitMs()} confirmation wait — this is not a
     * blind multiplier on the old "2 quick back-to-back reads" behavior).
     */
    @Key("selectdoc.scroll.noprogress.streak.required")
    @DefaultValue("3")
    int selectDocScrollNoProgressStreakRequired();

    @Key("selectdoc.focusrow.render.ms")
    @DefaultValue("1500")
    int selectDocFocusRowRenderMs();

    @Key("selectdoc.click.buffer.ms")
    @DefaultValue("300")
    int selectDocClickBufferMs();

    @Key("selectdoc.loader.max.retries")
    @DefaultValue("3")
    int selectDocLoaderMaxRetries();

    @Key("click.overlay.retry.max")
    @DefaultValue("3")
    int clickOverlayRetryMax();

    /**
     * Timeout for the post-module-click "Loading Package" bootstrap wait
     * ({@code TopModuleMenuComponent.navigateToModule()}) — deliberately separate from the
     * generic {@code timeout} and {@code extjs.timeout} tiers since this one-off, heavier
     * module-bootstrap step has a different SLA than a routine mask clear.
     */
    @Key("module.packageload.timeout.seconds")
    @DefaultValue("60")
    long modulePackageLoadTimeoutSeconds();

    /**
     * Timeout for confirming a module's dashboard header actually rendered after the heavier
     * module-bootstrap mask already cleared ({@code TopModuleMenuComponent.navigateToModule()})
     * — covers only the post-mask ExtJS panel-render race, so it stays short (should resolve in
     * seconds when healthy), unlike {@link #modulePackageLoadTimeoutSeconds()}.
     */
    @Key("dashboard.header.timeout.seconds")
    @DefaultValue("15")
    long dashboardHeaderTimeoutSeconds();

    /**
     * Global on/off switch for the post-menu-click dashboard-loaded verification. Defaults to
     * enabled — set to false in an environment/suite where the dashboard is known to be
     * disabled outright, to skip the check for every module without touching code.
     */
    @Key("dashboard.verification.enabled")
    @DefaultValue("true")
    boolean dashboardVerificationEnabled();

    /**
     * Comma-separated {@code MainModuleType} display names (e.g. "Finance,Inventory") excluded
     * from dashboard-loaded verification even when the global switch above is enabled — for a
     * particular module known to have its dashboard disabled/unsupported without disabling
     * verification everywhere. Empty by default (no module excluded).
     */
    @Key("dashboard.verification.disabled.modules")
    @DefaultValue("")
    String dashboardVerificationDisabledModules();

    @Key("popup.detection.enabled")
    @DefaultValue("true")
    boolean popupDetectionEnabled();

    @Key("popup.detection.timeout.ms")
    @DefaultValue("1500")
    int popupDetectionTimeoutMs();

    @Key("popup.detection.polling.ms")
    @DefaultValue("100")
    int popupDetectionPollingMs();

    @Key("doc.selector.mode")
    @DefaultValue("POSITIVE")
    String docSelectorMode();

    @Key("doc.selector.overfetch.multiplier")
    @DefaultValue("3")
    int docSelectorOverfetchMultiplier();

    @Key("doc.selector.retry.backoff.ms")
    @DefaultValue("1500")
    long docSelectorRetryBackoffMs();

    @Key("doc.selector.documentcount.default")
    @DefaultValue("1")
    int docSelectorDocumentCountDefault();
}
