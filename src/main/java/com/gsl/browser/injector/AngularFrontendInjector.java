package com.gsl.browser.injector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Injects Angular-specific performance instrumentation into the page.
 *
 * <p>Uses three available surfaces:
 * <ul>
 *   <li><b>{@code getAllAngularTestabilities()}</b> — standard Angular test API that
 *       reports NgZone stability.  Polled every 100 ms to record unstable intervals.</li>
 *   <li><b>{@code window.ng.profiler.timeChangeDetection()}</b> — dev-mode Angular
 *       profiler; captured when present, silently skipped in production.</li>
 *   <li><b>MutationObserver</b> — watches for CDK overlay backdrops, Angular Material
 *       spinners, and {@code [aria-busy]} elements.</li>
 * </ul>
 */
public final class AngularFrontendInjector implements FrontendDiagnosticsInjector {

    private static final Logger LOG = LogManager.getLogger(AngularFrontendInjector.class);

    private static final String COMMON_SCRIPT = """
            (function() {
                if (window.__gslExtDiagCommonInjected) return;
                window.__gslExtDiagCommonInjected = true;

                var _fetch = window.fetch;
                if (_fetch) {
                    window.fetch = function() {
                        var url = arguments[0] && arguments[0].url ? arguments[0].url : String(arguments[0]);
                        var t0  = performance.now();
                        return _fetch.apply(this, arguments).then(function(resp) {
                            window.__gslExtDiag.apiTimings.push({
                                url: url, durationMs: Math.round(performance.now() - t0), status: resp.status
                            });
                            return resp;
                        }, function(err) {
                            window.__gslExtDiag.apiTimings.push({
                                url: url, durationMs: Math.round(performance.now() - t0), status: 0
                            });
                            throw err;
                        });
                    };
                }

                var XHR   = XMLHttpRequest.prototype;
                var _open = XHR.open;
                var _send = XHR.send;
                XHR.open = function(method, url) { this.__gslUrl = url; return _open.apply(this, arguments); };
                XHR.send = function() {
                    var self = this, t0 = performance.now();
                    this.addEventListener('loadend', function() {
                        window.__gslExtDiag.apiTimings.push({
                            url: self.__gslUrl || '', durationMs: Math.round(performance.now() - t0), status: self.status
                        });
                    });
                    return _send.apply(this, arguments);
                };

                var _addEv = document.addEventListener.bind(document);
                document.addEventListener = function(type, fn, opts) {
                    var wrapped = function() {
                        var t0 = performance.now(), res = fn.apply(this, arguments), dur = Math.round(performance.now() - t0);
                        if (dur > 50) window.__gslExtDiag.slowEventHandlers.push({ event: type, durationMs: dur });
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

    private static final String ANGULAR_SCRIPT = """
            (function() {
                // ── layout-shift observer (CLS) ──────────────────────────────
                try {
                    new PerformanceObserver(function(list) {
                        list.getEntries().forEach(function(e) {
                            if (!e.hadRecentInput) {
                                var sources = [];
                                if (e.sources) {
                                    e.sources.forEach(function(s) {
                                        if (s.node) sources.push(s.node.nodeName || '');
                                    });
                                }
                                window.__gslExtDiag.layoutShifts.push({
                                    value:   Math.round(e.value * 10000) / 10000,
                                    sources: sources.join(', ')
                                });
                            }
                        });
                    }).observe({ type: 'layout-shift', buffered: true });
                } catch(ex) {}

                // ── NgZone stability polling ─────────────────────────────────
                var _ngUnstableAt = null;
                var _ngPollId = setInterval(function() {
                    try {
                        var testabilities = window.getAllAngularTestabilities
                            ? window.getAllAngularTestabilities() : [];
                        var allStable = testabilities.every(function(t) { return t.isStable(); });
                        if (!allStable && _ngUnstableAt == null) {
                            _ngUnstableAt = performance.now();
                        } else if (allStable && _ngUnstableAt != null) {
                            var dur = Math.round(performance.now() - _ngUnstableAt);
                            window.__gslExtDiag.ngZoneEvents.push({
                                unstableAt: Math.round(_ngUnstableAt),
                                stableAt:   Math.round(performance.now()),
                                durationMs: dur
                            });
                            _ngUnstableAt = null;
                        }
                    } catch(ex) {}
                }, 100);
                // Store interval ID so tests can clear it if needed
                window.__gslNgPollId = _ngPollId;

                // ── ng.profiler.timeChangeDetection() ────────────────────────
                if (typeof window.ng !== 'undefined' && window.ng.profiler) {
                    try {
                        var cdResult = window.ng.profiler.timeChangeDetection({ record: false });
                        if (cdResult && cdResult.msPerTick != null) {
                            window.__gslExtDiag.changeDetectionMs = Math.round(cdResult.msPerTick);
                        }
                    } catch(ex) {}
                }

                // ── MutationObserver for CDK overlays + Angular loading ───────
                var LOADING_SELECTOR =
                    '.cdk-overlay-backdrop,mat-progress-bar,mat-spinner,[aria-busy="true"],.ngx-loading,.ng-spinner';
                var obs = new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes.forEach(function(node) {
                            if (node.nodeType !== 1) return;
                            var sel = matchSelector(node, LOADING_SELECTOR);
                            if (sel) {
                                node.__gslLoadStart = performance.now();
                                node.__gslSelector  = sel;
                            }
                        });
                        m.removedNodes.forEach(function(node) {
                            if (node.nodeType !== 1 || !node.__gslLoadStart) return;
                            window.__gslExtDiag.loadingStates.push({
                                selector:  node.__gslSelector || 'loading',
                                durationMs: Math.round(performance.now() - node.__gslLoadStart)
                            });
                        });
                    });
                });
                obs.observe(document.body || document.documentElement,
                    { childList: true, subtree: true });

                // ── router-outlet navigation timing ──────────────────────────
                var _routerOutlet = document.querySelector('router-outlet');
                if (_routerOutlet) {
                    var _routeStart = performance.now();
                    new MutationObserver(function() {
                        var dur = Math.round(performance.now() - _routeStart);
                        window.__gslExtDiag.routeTransitions.push({ durationMs: dur });
                        _routeStart = performance.now();
                    }).observe(_routerOutlet, { attributes: true, childList: true });
                }

                function matchSelector(el, sel) {
                    try {
                        var parts = sel.split(',');
                        for (var i = 0; i < parts.length; i++) {
                            if (el.matches(parts[i].trim())) return parts[i].trim();
                        }
                        return null;
                    } catch(e) { return null; }
                }
            })();
            """;

    private static final String INIT_SCRIPT = """
            if (!window.__gslExtDiagInjected) {
                window.__gslExtDiagInjected = true;
                window.__gslExtDiag = {
                    framework:         'angular',
                    apiTimings:        [],
                    slowEventHandlers: [],
                    ngZoneEvents:      [],
                    changeDetectionMs: -1,
                    loadingStates:     [],
                    layoutShifts:      [],
                    routeTransitions:  [],
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
            js.executeScript(ANGULAR_SCRIPT);
        } catch (Exception e) {
            LOG.debug("[AngularFrontendInjector] Injection skipped: {}", e.getMessage());
        }
    }

    @Override
    public String frameworkName() {
        return "Angular";
    }
}
