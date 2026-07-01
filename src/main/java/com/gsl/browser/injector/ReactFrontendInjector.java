package com.gsl.browser.injector;

import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Injects React-specific performance instrumentation into the page.
 *
 * <p>React DevTools global hook ({@code __REACT_DEVTOOLS_GLOBAL_HOOK__}) is absent
 * in production builds.  This injector uses two surfaces that work without it:
 * <ul>
 *   <li><b>React User Timing marks</b> — React ≥16 inserts {@code ⚛ Component render}
 *       and {@code ⚛ Effect} measures in development / profiling builds.  Harvested
 *       via {@code performance.getEntriesByType('measure')} at collection time.</li>
 *   <li><b>MutationObserver</b> — watches for loading element appearance/disappearance
 *       ({@code [aria-busy]}, {@code [role=progressbar]}, {@code .loading},
 *       {@code .skeleton}) to measure overlay durations.</li>
 * </ul>
 *
 * <p>Note: {@code reactRenders[]} will be empty on production builds — a note is
 * shown in {@code FrontendPerformanceSection} when no marks are found.
 */
public final class ReactFrontendInjector implements FrontendDiagnosticsInjector {

    private static final Logger LOG = LogManager.getLogger(ReactFrontendInjector.class);

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

                var XHR  = XMLHttpRequest.prototype;
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

    private static final String REACT_SCRIPT = """
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

                // ── MutationObserver for loading states ──────────────────────
                var LOADING_SELECTOR = '[aria-busy="true"],[role="progressbar"],.loading,.skeleton,.spinner';
                var _activePending   = {};
                var obs = new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes.forEach(function(node) {
                            if (node.nodeType !== 1) return;
                            var sel = matchSelector(node, LOADING_SELECTOR);
                            if (sel) {
                                var key = sel + '_' + Date.now();
                                node.__gslLoadKey   = key;
                                node.__gslLoadStart = performance.now();
                                _activePending[key] = node.__gslLoadStart;
                            }
                        });
                        m.removedNodes.forEach(function(node) {
                            if (node.nodeType !== 1 || !node.__gslLoadKey) return;
                            var dur = Math.round(performance.now() - node.__gslLoadStart);
                            window.__gslExtDiag.loadingStates.push({
                                selector:  matchSelector(node, LOADING_SELECTOR) || 'loading',
                                durationMs: dur
                            });
                            delete _activePending[node.__gslLoadKey];
                        });
                    });
                });
                obs.observe(document.body || document.documentElement,
                    { childList: true, subtree: true });

                function matchSelector(el, sel) {
                    try { return el.matches(sel) ? sel : null; } catch(e) { return null; }
                }

                // ── history patch for route transitions ──────────────────────
                var _push  = history.pushState;
                var _t0Nav = performance.now();
                var _lastPath = location.pathname + location.hash;
                history.pushState = function() {
                    var dur = Math.round(performance.now() - _t0Nav);
                    window.__gslExtDiag.routeTransitions.push({
                        from:      _lastPath,
                        to:        arguments[2] || '',
                        durationMs: dur
                    });
                    _lastPath = arguments[2] || '';
                    _t0Nav    = performance.now();
                    return _push.apply(this, arguments);
                };
                window.addEventListener('popstate', function() {
                    var cur = location.pathname + location.hash;
                    window.__gslExtDiag.routeTransitions.push({
                        from:      _lastPath,
                        to:        cur,
                        durationMs: 0
                    });
                    _lastPath = cur;
                });
            })();
            """;

    private static final String INIT_SCRIPT = """
            if (!window.__gslExtDiagInjected) {
                window.__gslExtDiagInjected = true;
                window.__gslExtDiag = {
                    framework:         'react',
                    apiTimings:        [],
                    slowEventHandlers: [],
                    reactRenders:      [],
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
            js.executeScript(REACT_SCRIPT);
        } catch (Exception e) {
            LOG.debug("[ReactFrontendInjector] Injection skipped: {}", e.getMessage());
            ReportLogger.js("[ReactFrontendInjector] Injection skipped: {}", e.getMessage());
        }
    }

    @Override
    public String frameworkName() {
        return "React";
    }
}
