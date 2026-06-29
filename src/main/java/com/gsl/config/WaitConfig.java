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
    @DefaultValue("3")
    int docSelectorDocumentCountDefault();
}
