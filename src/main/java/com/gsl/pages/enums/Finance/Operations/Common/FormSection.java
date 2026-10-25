package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import org.openqa.selenium.By;

/**
 * Collapsible form sections, in one place: the exact on-screen title (matched against the DOM),
 * the component key used for the {@code SECTION_<key>} UI-validation result, and the title /
 * chevron locators. Section titles are {@code div.gsl_TitlePnlType1} display fields; the open/closed
 * chevron is an {@code a.panelheaderarrow > ... > i.mdi-chevron-*} in the same header container.
 *
 * <p>Shared by GV/AR/AP (Document Information, Payment Information, and the domain's own second
 * section) and by the Allocate Sites popup's three sections — nothing else should hard-code these
 * title strings.
 */
public enum FormSection {

    DOCUMENT_INFORMATION("Document Information"),
    EXPENSE_INCOME_DETAIL("Expense/Income Detail"),
    SUB_LEDGER_INFORMATION("Sub Ledger Information"),
    ADJUSTMENT_INFORMATION("Adjustment Information"),
    PAYMENT_INFORMATION("Payment Information"),
    ALLOCATE_SITES_QUICK_SITE_ALLOCATE("Quick Site Allocate"),
    ALLOCATE_SITES_ALLOCATED_SITES("Allocated Site(s)"),
    ALLOCATE_SITES_TAGGING_SUMMARY("Tagging Summary");

    /** Chevron icon class when the section is expanded (confirmed from the live DOM + screenshot). */
    public static final String CHEVRON_OPEN_CLASS = "mdi-chevron-down";
    /** Chevron icon class when the section is collapsed. */
    public static final String CHEVRON_CLOSED_CLASS = "mdi-chevron-up";

    private final String title;

    FormSection(String title) {
        this.title = title;
    }

    /** The exact on-screen title text. */
    public String title() { return title; }

    /** The component key, e.g. {@code DOCUMENT_INFORMATION} — {@code SECTION_<key>} in results. */
    public String key() { return name(); }

    /** The section title element, under {@code scopeXpath} ({@code ""} for the whole page). */
    public By titleLocator(String scopeXpath) {
        return By.xpath(scopeXpath + titleXpath());
    }

    /** The open/closed chevron icon: from the title, the nearest ancestor that contains a
     *  {@code mdi-chevron-*} icon (that section's own header container), then that icon. The single
     *  {@code [1]} is required — it picks the nearest ancestor on a reverse axis. */
    public By chevronLocator(String scopeXpath) {
        return By.xpath(scopeXpath + titleXpath()
                + "/ancestor::*[.//i[contains(@class,'mdi-chevron-')]][1]//i[contains(@class,'mdi-chevron-')]");
    }

    /** XPath fragment (starting {@code //div[...]}) of this section's title element, for callers that
     *  need to navigate relative to the title, e.g. {@link PaymentPanelField}. */
    public String titleXpath() {
        return "//div[contains(@class,'gsl_TitlePnlType1') and normalize-space(.)='" + title + "']";
    }

    /** Scope for the voucher Add/Edit window of {@code domain}: either title's window ancestor, so
     *  the list page behind the form is never matched. Append {@code //...} to it. */
    public static String voucherWindowScope(VoucherDomain domain) {
        return "(" + windowOf(domain.getAddFormHeader()) + " | " + windowOf(domain.getEditFormHeader()) + ")";
    }

    private static String windowOf(String windowTitle) {
        return "//div[contains(@class,'winTitleType1') and normalize-space()='" + windowTitle + "']"
                + "/ancestor::div[contains(@class,'x-window')][1]";
    }
}
