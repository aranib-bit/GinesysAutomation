package com.gsl.extjs;

import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Framework-agnostic contract for UI component diagnostics.
 * Implementations exist for ExtJS, React, and Angular.
 * Switch via application.type in config.properties — no code change required.
 *
 * Isolation rule: this package knows only WebDriver and Java.
 * Zero imports from com.gsl.browser, com.gsl.diagnostics, or com.gsl.reports.
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
