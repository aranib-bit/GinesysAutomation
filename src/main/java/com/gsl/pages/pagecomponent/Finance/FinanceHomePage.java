package com.gsl.pages.pagecomponent.Finance;

import com.gsl.config.ConfigFactory;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.topmenucomponent.DashboardTitle;
import com.gsl.utils.DashboardHeaderUtils;
import org.openqa.selenium.By;

/**
 * Page object for the Finance module's landing dashboard — the page a test lands on right after
 * navigating into Finance, before drilling into a specific sub-menu.
 *
 * <p>Exposes its {@link FinanceTopMenuComponent} for switching top-menu tabs (e.g. Operations vs
 * Setup — see {@link com.gsl.pages.enums.Finance.MainMenuType}), and implements {@link
 * VerifiablePage} so navigation helpers can confirm the dashboard actually rendered before
 * proceeding.
 */
public class FinanceHomePage implements VerifiablePage {

    private FinanceTopMenuComponent financeTopMenuComponent;

    public FinanceHomePage() {
        this.financeTopMenuComponent = new FinanceTopMenuComponent();
    }

    /** @return this page's {@link FinanceTopMenuComponent}, for switching top-menu tabs. */
    public FinanceTopMenuComponent getFinanceTopMenuComponent() {
        return financeTopMenuComponent;
    }

    /**
     * Confirms the Finance Dashboard panel actually rendered its header — see
     * {@code TopModuleMenuComponent.navigateToModule()} for how a failure here triggers a
     * refresh-based recovery attempt before being treated as a real failure.
     *
     * <p>The locator embeds the expected title text (rather than matching any {@code
     * dashboardHeading} node generically) because the app can momentarily render more than one
     * such node during a module transition — confirmed live via DevTools: the xpath without the
     * text predicate matched 3 elements, and the first DOM match (what {@code
     * ExpectedConditions.visibilityOfElementLocated} inspects) was not the live "Finance
     * Dashboard" panel, so the wait timed out even though the real header was visible among the
     * other matches. Baking the title into the locator makes a stale/other node structurally
     * unable to match.
     */
    @Override
    public void verifyOnThisPage() {
        String expectedTitle = DashboardTitle.FINANCE.getExpectedTitle();
        By header = By.xpath(
                "//div[contains(concat(' ',normalize-space(@class),' '),' dashboardHeading ')]"
                        // normalize-space(.) (whole-element string-value), not normalize-space(text())
                        // (first direct text-node child only) — matches what el.getText().trim() checks
                        // downstream in DashboardHeaderUtils even if the title wraps its text in an inline node.
                        + "//div[contains(@class,'x-title-text') and normalize-space(.)='"
                        + expectedTitle + "']");
        DashboardHeaderUtils.verifyDashboardHeader(header, expectedTitle,
                ConfigFactory.getWaitConfig().dashboardHeaderTimeoutSeconds());
    }
}
