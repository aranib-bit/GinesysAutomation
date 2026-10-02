package com.gsl.extjs;

import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Framework-agnostic contract for UI component diagnostics — implemented once per front-end
 * stack and selected at runtime by {@link UiFrameworkAdapterFactory} via {@code application.type}
 * in config.properties, so switching frameworks needs no code change.
 *
 * <p>Isolation rule: this package knows only {@link WebDriver} and Java — zero imports from
 * {@code com.gsl.browser}, {@code com.gsl.diagnostics}, or {@code com.gsl.reports}.
 *
 * <ul>
 *   <li>{@link ExtJsUiAdapter}</li>
 *   <li>{@link ReactUiAdapter}</li>
 *   <li>{@link AngularUiAdapter}</li>
 *   <li>{@link UiFrameworkAdapterFactory}</li>
 * </ul>
 */
public interface UiFrameworkAdapter {

    /** Top-N component states scraped from the live browser. */
    List<ComponentState> getComponentDiagnostics(WebDriver driver);

    /** Active application module name (e.g. "Finance", app title, or router module). */
    String getActiveModule(WebDriver driver);

    /** Active tab label within the current module, or empty string if not applicable. */
    String getActiveTab(WebDriver driver);

    /** Current client-side route (hash or pathname). */
    String getActiveRoute(WebDriver driver);

    /** Short display name shown in the section title: "ExtJS" | "React" | "Angular". */
    String frameworkName();
}
