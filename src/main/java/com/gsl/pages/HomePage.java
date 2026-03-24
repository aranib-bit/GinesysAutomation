package com.gsl.pages;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.TopModuleMenuComponent;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.click;

public class HomePage {
    private static final By IMG_USERNAME = By.xpath("//img[contains(@class, 'x-img') and contains(@class, 'h-profile-img') and contains(@class, 'x-box-item') and contains(@class, 'x-img-default') and @alt='User Profile']");
    private static final By BTN_LOGOUT=By.xpath("//span[text()='Logout' and contains(@class,'x-btn') and contains(@class,'x-btn-inner-default-small')]");

    private TopModuleMenuComponent topModuleMenuComponent;

//    public String getHomePageTitle() {
//        return DriverManager.getDriver().getTitle();
//    }

    public HomePage() {
        this.topModuleMenuComponent = new TopModuleMenuComponent();
    }

    public TopModuleMenuComponent getTopModuleMenuComponent() {
        return topModuleMenuComponent;
    }


    public LoginPage logoutFromApplication() {
        SeleniumActions.moveToElementAndClick(IMG_USERNAME);
        SeleniumActions.moveToElementAndClick(BTN_LOGOUT);

        //SeleniumActions.clickAndHoldThenClick(IMG_USERNAME,BTN_LOGOUT);
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "GINESYS LOADER");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new LoginPage();
    }


}
