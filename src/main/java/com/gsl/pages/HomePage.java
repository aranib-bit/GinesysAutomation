package com.gsl.pages;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.TopModuleMenuComponent;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.click;

public class HomePage {
    private static final By IMG_USERNAME = By.xpath("//img[contains(@class, 'x-img') and contains(@class, 'h-profile-img') and contains(@class, 'x-box-item') and contains(@class, 'x-img-default') and @alt='User Profile']");

    /**
     * The Logout menu item inside the (lazily-rendered) profile menu.
     *
     * <p>The previous locator also required {@code contains(@class,'x-btn')} and
     * {@code contains(@class,'x-btn-inner-default-small')}, which DOM forensics showed to be the
     * wrong class family entirely: in this app {@code x-btn-inner-default-small} belongs to
     * <b>message-box</b> buttons (OK/Yes/No/Cancel). Toolbar buttons use
     * {@code x-btn-inner-default-toolbar-small} — which does not contain that substring — and
     * header/profile-menu items render as {@code span.x-menu-item-text}, not {@code x-btn-inner}
     * at all (proven by the Actions dropdown's own markup). Matching on the visible text alone is
     * both correct and sufficient here: {@code Logout} appears nowhere else once the menu is open.
     */
    private static final By BTN_LOGOUT = By.xpath("//span[normalize-space()='Logout']");

    /** Short, bounded wait for the profile menu's lazily-rendered contents — see
     *  {@link #logoutFromApplication()} for why this is deliberately not the global 30s. */
    private static final int PROFILE_MENU_WAIT_SECONDS = 5;

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


    /**
     * Opens the profile menu and clicks Logout.
     *
     * <p><b>Why this is more than two clicks.</b> This method used to fail at session drain on
     * every {@code <test>} block, burning 30s per attempt and leaking a server-side app session,
     * with a {@code NoSuchElementException} on the Logout span. DOM forensics found three
     * compounding causes:
     * <ol>
     *   <li>{@code Logout} appears <b>zero times in every captured DOM snapshot in this repo</b> —
     *       it is <i>absent</i>, not hidden. The profile menu is lazily rendered (same as the
     *       Actions dropdown), so it simply does not exist until the profile image click lands.</li>
     *   <li>That click was not landing: the captures show a full-viewport {@code x-mask} (and in
     *       one case {@code x-masked} on {@code <body>}) sitting over the page at drain time, and
     *       {@link SeleniumActions#moveToElementAndClick} uses an {@code Actions}-based click,
     *       which performs <b>no click-interception check and throws nothing</b> when a mask eats
     *       the click. {@code elementToBeClickable} doesn't help either — it checks displayed +
     *       enabled, not occluded. So the failure surfaced 30s later on the wrong element.</li>
     *   <li>The old Logout locator's class predicates were from the wrong class family — see
     *       {@link #BTN_LOGOUT}.</li>
     * </ol>
     * So: clear the overlay <i>first</i> (it used to be cleared only <i>after</i> both clicks),
     * then click the profile image, then verify the menu actually rendered before going for
     * Logout, re-clicking once if it didn't.
     *
     * <p>Waits here are deliberately short rather than the global 30s. This is best-effort
     * teardown — both callers ({@code SessionPool.drainAll},
     * {@code SessionManager.closeSessionAndReleaseUser}) already catch and continue to
     * {@code destroy()} — so a long wait never bought anything but a slower failure.
     */
    public LoginPage logoutFromApplication() {
        // 1. Clear any blocking mask BEFORE clicking — best-effort, since teardown must never
        //    hard-fail on a stuck/zero-size mask.
        WaitUtils.waitForExtMaskBestEffort("pre-logout-mask-clear");

        // 2. Open the profile menu, and confirm its lazily-rendered contents actually appeared.
        SeleniumActions.moveToElementAndClick(IMG_USERNAME);
        if (!SeleniumUtils.isElementPresent(BTN_LOGOUT, PROFILE_MENU_WAIT_SECONDS)) {
            Log.warn("[HomePage] Profile menu did not render Logout after the first click "
                    + "(the click may have been intercepted by a mask) — retrying once");
            WaitUtils.waitForExtMaskBestEffort("pre-logout-mask-clear-retry");
            SeleniumActions.moveToElementAndClick(IMG_USERNAME);
        }

        // 3. Click Logout.
        SeleniumActions.moveToElementAndClick(BTN_LOGOUT);

        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "GINESYS LOADER");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new LoginPage();
    }


}
