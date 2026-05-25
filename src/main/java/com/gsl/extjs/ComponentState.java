package com.gsl.extjs;

import java.util.Map;

/**
 * Framework-agnostic snapshot of a single UI component at the time of diagnostic capture.
 *
 * Field mapping by framework:
 *   name             — xtype (ExtJS) | displayName/name from fiber (React) | tag/selector (Angular)
 *   id               — itemId (ExtJS) | React key (React) | element id/tag (Angular)
 *   rendered         — c.rendered (ExtJS) | fiber mounted (React) | element in DOM (Angular)
 *   destroyed        — c.destroyed (ExtJS) | fiber unmounted (React) | false for Angular
 *   hidden           — c.hidden (ExtJS) | CSS visibility (React/Angular)
 *   disabled         — c.disabled (ExtJS) | disabled attr (React/Angular)
 *   elPresent        — c.el != null (ExtJS) | stateNode in document (React) | element in body (Angular)
 *   frameworkVersion — Ext.getVersion().version | React.version | ng-version attribute
 *   extra            — framework-specific extras (hasErrorBoundary, changeDetection, selector, etc.)
 */
public record ComponentState(
        String              name,
        String              id,
        boolean             rendered,
        boolean             destroyed,
        boolean             hidden,
        boolean             disabled,
        boolean             elPresent,
        String              frameworkVersion,
        Map<String, String> extra
) {}
