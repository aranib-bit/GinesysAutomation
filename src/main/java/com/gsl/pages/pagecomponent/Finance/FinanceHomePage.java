package com.gsl.pages.pagecomponent.Finance;

import com.gsl.config.ConfigFactory;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.topmenucomponent.DashboardTitle;
import com.gsl.utils.DashboardHeaderUtils;
import org.openqa.selenium.By;

public class FinanceHomePage implements VerifiablePage {

    private static final By DASHBOARD_HEADER = By.xpath(
            "//div[contains(concat(' ',normalize-space(@class),' '),' dashboardHeading ')]"
                    + "//div[contains(@class,'x-title-text')]");

    private FinanceTopMenuComponent financeTopMenuComponent;

    public FinanceHomePage() {
        this.financeTopMenuComponent = new FinanceTopMenuComponent();
    }

    public FinanceTopMenuComponent getFinanceTopMenuComponent() {
        return financeTopMenuComponent;
    }

    /**
     * Confirms the Finance Dashboard panel actually rendered its header — see
     * {@code TopModuleMenuComponent.navigateToModule()} for how a failure here triggers a
     * refresh-based recovery attempt before being treated as a real failure.
     */
    @Override
    public void verifyOnThisPage() {
        DashboardHeaderUtils.verifyDashboardHeader(DASHBOARD_HEADER, DashboardTitle.FINANCE.getExpectedTitle(),
                ConfigFactory.getWaitConfig().dashboardHeaderTimeoutSeconds());
    }
}
