package com.gsl.diagnostics.section.frontend;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.section.frontend.angular.AngularPerformanceSection;
import com.gsl.diagnostics.section.frontend.extjs.ExtJsPerformanceSection;
import com.gsl.diagnostics.section.frontend.react.ReactPerformanceSection;
import org.openqa.selenium.WebDriver;

/**
 * Creates the correct {@link FrontendPerformanceSection} implementation based on
 * {@code application.type} in config.properties.
 *
 * Supported values (case-insensitive):
 *   react   → {@link ReactPerformanceSection}
 *   angular → {@link AngularPerformanceSection}
 *   extjs   → {@link ExtJsPerformanceSection}  (default)
 */
public final class FrontendPerformanceSectionFactory {

    private FrontendPerformanceSectionFactory() {}

    public static FrontendPerformanceSection create(WebDriver driver) {
        String type = ConfigFactory.getConfig().applicationType().toLowerCase().trim();
        return switch (type) {
            case "react"   -> new ReactPerformanceSection(driver);
            case "angular" -> new AngularPerformanceSection(driver);
            default        -> new ExtJsPerformanceSection(driver);
        };
    }
}
