package com.gsl.browser.injector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Injects ExtJS-specific performance instrumentation into the page.
 *
 * <p>Monkey-patches key ExtJS prototype methods to buffer timing data into
 * {@code window.__gslExtDiag}.  All patches are applied via {@code Ext.onReady()}
 * so they are guaranteed to run after ExtJS has fully initialised, even when
 * Selenium's {@code driver.get()} returns before the Ext namespace is available.
 * A {@code setInterval} fallback (200 ms × 150 attempts = 30 s) covers pages
 * that load ExtJS asynchronously after the initial page-load event.
 *
 * <p>Captures:
 * <ul>
 *   <li>LoadMask START/END pairs → {@code loadMaskEvents[]}</li>
 *   <li>MessageBox show events  → {@code messageBoxEvents[]}</li>
 *   <li>Component afterRender   → {@code componentRenders[]} (only &gt;50 ms)</li>
 *   <li>Layout context runs     → {@code layoutRuns[]} (only &gt;50 ms)</li>
 *   <li>Slow event handlers     → {@code slowEventHandlers[]} (only &gt;50 ms)</li>
 *   <li>fetch / XHR timings     → {@code apiTimings[]} (common script)</li>
 * </ul>
 */
public final class ExtJsFrontendInjector implements FrontendDiagnosticsInjector {

    private static final Logger LOG = LogManager.getLogger(ExtJsFrontendInjector.class);

    // ── Common script — fetch, XHR, slow DOM event handler wrappers ─────────

    private static final String COMMON_SCRIPT = """
            (function() {
                if (window.__gslExtDiagCommonInjected) return;
                window.__gslExtDiagCommonInjected = true;

                // ── fetch wrapper ────────────────────────────────────────────
                var _fetch = window.fetch;
                if (_fetch) {
                    window.fetch = function() {
                        var url  = arguments[0] && arguments[0].url ? arguments[0].url : String(arguments[0]);
                        var t0   = performance.now();
                        return _fetch.apply(this, arguments).then(function(resp) {
                            window.__gslExtDiag.apiTimings.push({
                                url:       url,
                                durationMs: Math.round(performance.now() - t0),
                                status:    resp.status
                            });
                            return resp;
                        }, function(err) {
                            window.__gslExtDiag.apiTimings.push({
                                url:       url,
                                durationMs: Math.round(performance.now() - t0),
                                status:    0
                            });
                            throw err;
                        });
                    };
                }

                // ── XHR wrapper ──────────────────────────────────────────────
                var XHR = XMLHttpRequest.prototype;
                var _open = XHR.open;
                var _send = XHR.send;
                XHR.open = function(method, url) {
                    this.__gslUrl = url;
                    return _open.apply(this, arguments);
                };
                XHR.send = function() {
                    var self = this;
                    var t0   = performance.now();
                    this.addEventListener('loadend', function() {
                        window.__gslExtDiag.apiTimings.push({
                            url:       self.__gslUrl || '',
                            durationMs: Math.round(performance.now() - t0),
                            status:    self.status
                        });
                    });
                    return _send.apply(this, arguments);
                };

                // ── slow DOM event handler wrapper ───────────────────────────
                var _addEv = document.addEventListener.bind(document);
                document.addEventListener = function(type, fn, opts) {
                    var wrapped = function() {
                        var t0  = performance.now();
                        var res = fn.apply(this, arguments);
                        var dur = Math.round(performance.now() - t0);
                        if (dur > 50) {
                            window.__gslExtDiag.slowEventHandlers.push({ event: type, durationMs: dur });
                        }
                        return res;
                    };
                    return _addEv(type, wrapped, opts);
                };

                // ── forced reflow detector (getBoundingClientRect > 5ms) ─────
                (function(){
                    var _gbc = Element.prototype.getBoundingClientRect;
                    Element.prototype.getBoundingClientRect = function() {
                        var t0  = performance.now();
                        var r   = _gbc.apply(this, arguments);
                        var dur = Math.round(performance.now() - t0);
                        if (dur > 5 && window.__gslExtDiag) {
                            window.__gslExtDiag.forcedReflows.push({
                                tagName:   this.tagName  || '',
                                durationMs: dur
                            });
                        }
                        return r;
                    };
                })();

                // ── user interaction tracker (click events) ──────────────────
                document.addEventListener('click', function(e) {
                    if (!e.target || !window.__gslExtDiag) return;
                    var cn = e.target.className;
                    window.__gslExtDiag.interactions.push({
                        tagName:   e.target.tagName || '',
                        className: (cn && typeof cn === 'string')
                                    ? cn.split(' ').slice(0, 3).join(' ') : '',
                        timestamp: Math.round(performance.now())
                    });
                }, true);
            })();
            """;

    // ── ExtJS-specific patches ────────────────────────────────────────────────

    private static final String EXTJS_SCRIPT = """
            (function() {
                // Outer guard: entire script is a no-op if patches are already applied.
                // Without this, Ext.onReady() is called on every step even when the page
                // is already patched — and if ExtJS is mid-render that enqueue blocks the
                // JS engine for 1-4 seconds, creating a silent gap between step durations.
                if (window.__gslExtJsPatched) return;

                function applyExtJsPatches() {
                    // Guard: skip if already patched in this page lifecycle
                    if (window.__gslExtJsPatched) return;
                    window.__gslExtJsPatched = true;

                    // ── setLoading (LoadMask timing) ─────────────────────────
                    var _setLoading = Ext.Component.prototype.setLoading;
                    Ext.Component.prototype.setLoading = function(load) {
                        if (load) {
                            this.__gslLoadStart = performance.now();
                            window.__gslExtDiag.loadMaskEvents.push({
                                component: this.itemId || this.id || '',
                                timestamp: Math.round(this.__gslLoadStart),
                                durationMs: 0,
                                _open: true
                            });
                            this.__gslLoadIdx = window.__gslExtDiag.loadMaskEvents.length - 1;
                        } else if (this.__gslLoadStart != null) {
                            var dur = Math.round(performance.now() - this.__gslLoadStart);
                            var idx = this.__gslLoadIdx;
                            if (idx != null && window.__gslExtDiag.loadMaskEvents[idx]) {
                                window.__gslExtDiag.loadMaskEvents[idx].durationMs = dur;
                                window.__gslExtDiag.loadMaskEvents[idx]._open = false;
                            }
                            this.__gslLoadStart = null;
                        }
                        return _setLoading.apply(this, arguments);
                    };

                    // ── Ext.Msg.show (MessageBox events) ─────────────────────
                    if (Ext.Msg) {
                        var _show = Ext.Msg.show;
                        Ext.Msg.show = function(config) {
                            window.__gslExtDiag.messageBoxEvents.push({
                                title:     (config && config.title)    || '',
                                msg:       (config && config.msg)      || '',
                                progress:  !!(config && config.progress),
                                timestamp: Math.round(performance.now())
                            });
                            return _show.apply(this, arguments);
                        };
                    }

                    // ── afterRender (component render timing) ────────────────
                    var _afterRender = Ext.Component.prototype.afterRender;
                    Ext.Component.prototype.afterRender = function() {
                        var t0  = performance.now();
                        var res = _afterRender.apply(this, arguments);
                        var dur = Math.round(performance.now() - t0);
                        if (dur > 50) {
                            window.__gslExtDiag.componentRenders.push({
                                xtype:     this.xtype  || '',
                                itemId:    this.itemId || this.id || '',
                                durationMs: dur
                            });
                        }
                        return res;
                    };

                    // ── Ext.layout.Context.run (layout calculation) ──────────
                    if (Ext.layout && Ext.layout.Context && Ext.layout.Context.prototype) {
                        var _run = Ext.layout.Context.prototype.run;
                        Ext.layout.Context.prototype.run = function() {
                            var t0  = performance.now();
                            var res = _run.apply(this, arguments);
                            var dur = Math.round(performance.now() - t0);
                            if (dur > 50) {
                                window.__gslExtDiag.layoutRuns.push({ durationMs: dur });
                            }
                            return res;
                        };
                    }

                    // ── Ext.Component.on (slow event handlers) ───────────────
                    var _on = Ext.Component.prototype.on;
                    Ext.Component.prototype.on = function(event, fn, scope, opts) {
                        var wrappedFn = fn;
                        if (typeof fn === 'function') {
                            wrappedFn = function() {
                                var t0  = performance.now();
                                var res = fn.apply(this, arguments);
                                var dur = Math.round(performance.now() - t0);
                                if (dur > 50) {
                                    window.__gslExtDiag.slowEventHandlers.push({
                                        event:     event,
                                        durationMs: dur
                                    });
                                }
                                return res;
                            };
                        }
                        return _on.call(this, event, wrappedFn, scope, opts);
                    };

                    // ── Ext.grid.Panel.afterRender (row + column count) ──────
                    if (Ext.grid && Ext.grid.Panel && Ext.grid.Panel.prototype) {
                        var _gridRender = Ext.grid.Panel.prototype.afterRender;
                        Ext.grid.Panel.prototype.afterRender = function() {
                            var t0  = performance.now();
                            var res = _gridRender.apply(this, arguments);
                            window.__gslExtDiag.gridRenders.push({
                                gridId:    this.id     || '',
                                itemId:    this.itemId || '',
                                rows:      (this.store && this.store.getCount)
                                            ? this.store.getCount() : 0,
                                columns:   this.columns ? this.columns.length : 0,
                                durationMs: Math.round(performance.now() - t0)
                            });
                            return res;
                        };
                    }
                }

                // ── Dispatcher: Ext.onReady if Ext already present, else poll ─
                if (typeof Ext !== 'undefined') {
                    Ext.onReady(applyExtJsPatches);
                } else {
                    var attempts = 0;
                    var pollId = setInterval(function() {
                        attempts++;
                        if (typeof Ext !== 'undefined') {
                            clearInterval(pollId);
                            Ext.onReady(applyExtJsPatches);
                        } else if (attempts >= 150) {
                            clearInterval(pollId);
                        }
                    }, 200);
                }
            })();
            """;

    // ── Initialisation script — sets up window.__gslExtDiag ──────────────────

    private static final String INIT_SCRIPT = """
            if (!window.__gslExtDiagInjected) {
                window.__gslExtDiagInjected = true;
                window.__gslExtDiag = {
                    framework:         'extjs',
                    apiTimings:        [],
                    slowEventHandlers: [],
                    loadMaskEvents:    [],
                    messageBoxEvents:  [],
                    componentRenders:  [],
                    layoutRuns:        [],
                    gridRenders:       [],
                    forcedReflows:     [],
                    interactions:      []
                };
            }
            """;

    @Override
    public void inject(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(INIT_SCRIPT);
            js.executeScript(COMMON_SCRIPT);
            js.executeScript(EXTJS_SCRIPT);
        } catch (Exception e) {
            LOG.debug("[ExtJsFrontendInjector] Injection skipped: {}", e.getMessage());
        }
    }

    @Override
    public String frameworkName() {
        return "ExtJS";
    }
}
