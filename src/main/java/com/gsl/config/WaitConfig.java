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

    /**
     * Round 10 (RC-11): gap before re-confirming a checkbox is STILL checked after {@code
     * waitUntilChecked} already returned true once. Closes a confirmed race where an async grid
     * reload (triggered by a preceding Unpost/Revert round-trip, and/or the exact-match filter's
     * own documented "asynchronous settle" behavior) silently clears row selection moments after
     * it was genuinely registered — a single instantaneous "checked" read cannot catch this, only
     * a second, gap-separated read can. Mirrors the same re-confirmed-stability idiom already
     * proven for the GV Edit scroll false-positive fix ({@link #selectDocScrollNoProgressConfirmWaitMs()}).
     */
    @Key("selectdoc.stability.recheck.delay.ms")
    @DefaultValue("400")
    int selectDocStabilityRecheckDelayMs();

    /**
     * Round 10 (RC-11): max select+stability-recheck attempts in {@code
     * VoucherMainGridBase.clickCheckbox} before giving up — bounded recovery from the async
     * reload race above, not a blind timeout increase (each attempt is a real re-click + a real
     * re-verify, never just re-reading the same already-lost state).
     */
    @Key("selectdoc.max.select.attempts")
    @DefaultValue("3")
    int selectDocMaxSelectAttempts();

    @Key("click.overlay.retry.max")
    @DefaultValue("3")
    int clickOverlayRetryMax();

    /**
     * Initial wait (seconds) for {@code #splashScreen} to genuinely disappear, inside {@code
     * WaitUtils.waitForClickOverlay()}'s splash-screen phase — see {@link #splashscreenPollSeconds()}
     * for the staged polls that follow if the splash screen is still visible after this.
     */
    @Key("splashscreen.initial.wait.seconds")
    @DefaultValue("30")
    long splashscreenInitialWaitSeconds();

    /**
     * Length (seconds) of each follow-up poll if {@link #splashscreenInitialWaitSeconds()}
     * wasn't enough — logged individually (see {@code WaitUtils.waitForSplashScreenClear}) so a
     * genuinely-stuck splash screen leaves a full timeline in the report instead of one opaque
     * timeout.
     */
    @Key("splashscreen.poll.seconds")
    @DefaultValue("15")
    long splashscreenPollSeconds();

    /** Number of {@link #splashscreenPollSeconds()} polls attempted before giving up. */
    @Key("splashscreen.poll.max.retries")
    @DefaultValue("4")
    int splashscreenPollMaxRetries();

    /**
     * Round 8: length (seconds) of each follow-up poll if the ExtJS {@code .x-mask}/{@code
     * .x-mask-msg} wait's initial {@code extjs.timeout} (40s) wasn't enough — mirrors {@link
     * #splashscreenPollSeconds()}'s already-proven staged-poll shape, applied to the mask wait
     * ({@code WaitUtils.waitForExtMaskInternal}) instead of only the splash screen.
     */
    @Key("mask.poll.seconds")
    @DefaultValue("15")
    long maskPollSeconds();

    /** Number of {@link #maskPollSeconds()} polls attempted before giving up (worst case:
     *  {@code extjs.timeout} + {@code maskPollMaxRetries() x maskPollSeconds()} = 40+4x15=100s). */
    @Key("mask.poll.max.retries")
    @DefaultValue("4")
    int maskPollMaxRetries();

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
