package com.gsl.utils.Selenium;

/**
 * Bundles the screenshot file path with page-context metadata captured at the
 * same instant as the screenshot.  All fields except {@code filePath} are
 * best-effort — they default to empty string / 0 when the JS round-trip fails.
 */
public record ScreenshotMetadata(
        String filePath,
        String pageUrl,
        long   scrollY,
        String viewport,
        String currentStep,
        /** Last element locator used before failure (from {@link CurrentLocatorHolder}). Null when unavailable. */
        String failedLocator
) {
    /** Single-line annotation suitable for attaching to an Extent log entry. */
    public String toAnnotation() {
        StringBuilder sb = new StringBuilder();
        if (!pageUrl.isBlank())     sb.append("URL: ").append(pageUrl).append("  |  ");
        if (scrollY > 0)            sb.append("ScrollY: ").append(scrollY).append("px  |  ");
        if (!viewport.isBlank())    sb.append("Viewport: ").append(viewport).append("  |  ");
        if (!currentStep.isBlank()) sb.append("Step: ").append(currentStep).append("  |  ");
        if (failedLocator != null && !failedLocator.isBlank())
                                    sb.append("Locator: ").append(failedLocator).append("  |  ");
        String s = sb.toString();
        return s.endsWith("  |  ") ? s.substring(0, s.length() - 5) : s;
    }
}
