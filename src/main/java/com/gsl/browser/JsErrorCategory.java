package com.gsl.browser;

/** Semantic root-cause buckets a CRITICAL {@link BrowserLogRecord} can be classified into by {@link JsErrorClassificationEngine}. */
public enum JsErrorCategory {
    EXTJS_COMPONENT_LIFECYCLE,
    NETWORK_RESPONSE_FAILURE,
    STORE_LOAD_FAILURE,
    DOM_REFERENCE_FAILURE,
    RACE_CONDITION,
    ASYNC_CALLBACK_FAILURE,
    UNKNOWN
}
