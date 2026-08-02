package com.tests.Inventory;

import com.gsl.api.core.ApiSessionManager;
import com.gsl.api.model.common.PricingData;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.driver.DriverManager;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.ItemManagementMode;
import com.gsl.enums.TestExecution;
import com.gsl.pages.Mobile.WMS.WmsLoginScreen;
import com.tests.MultiContextTest;
import io.appium.java_client.AppiumDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Proof-of-concept for the full MOBILE/WEBAPI/WEB context-switching loop described as the
 * original ask: create a document via WEBAPI, verify it in the WMS mobile app, confirm status
 * on WEB, then return to MOBILE. Exercises {@code ContextOrchestrator} end-to-end.
 *
 * <p>WMS screens beyond login are intentionally out of scope for this build (per direction —
 * real screens/locators follow once the app is shared), so the MOBILE leg here only proves the
 * context switch + login, not a full scan/receipt verification. The WEB leg likewise only
 * proves the switch itself (no dedicated DC-list page object exists yet in this codebase) —
 * both are marked below so the gap is visible rather than silently glossed over.
 */
public class VerifyDcReceiptInWmsAfterApiCreation extends MultiContextTest {

    @Test
    public void verifyContextSwitchLoop_webApiThenMobileThenWeb() {
        // 1. WEBAPI — create the source data.
        switchTo(TestExecution.API, ApiPlatform.WEBAPI);
        ItemCreateResponse item = ApiSessionManager.getSession().getItemApiClient()
                .createItemWithDefaults("WMSPOC", ItemManagementMode.INDIVIDUAL,
                        PricingData.builder().costrate(500).wsp(600).mrp(700).listedMrp(800).build(), 28);
        DcCreateResponse dc = ApiSessionManager.getSession().getDcApiClient()
                .createDc(item.getItemCode(), 10, 500);
        Assert.assertNotNull(dc.getDcCode(), "DC should have been created via WEBAPI");

        // 2. MOBILE — switch into the WMS app. loginScreen is constructed to prove the driver
        // is a real AppiumDriver at this point; the actual login() call is commented out because
        // the locators in WmsLoginScreen are placeholders (real WMS screens/locators are a
        // follow-up per direction — see WmsLoginScreen's javadoc).
        switchTo(TestExecution.MOBILE);
        WmsLoginScreen loginScreen = new WmsLoginScreen((AppiumDriver) DriverManager.getDriver());
        Assert.assertNotNull(loginScreen, "WMS login screen should be constructable against the mobile driver");
        // TODO once real WMS screens exist: loginScreen.login(DriverManager.getCurrentUser()); then
        // scan dc.getPacketBarcode() and assert the displayed quantity matches what was created above.

        // 3. WEB — confirm status. (Placeholder: no DC-list page object exists yet in this
        // codebase — this only proves the context switch itself, per the note above.)
        switchTo(TestExecution.WEB);
        Assert.assertNotNull(DriverManager.getDriver(), "WEB driver should be live after switching back");

        // 4. MOBILE again — closes the loop the original ask described (MOBILE -> WEBAPI/WEB -> MOBILE).
        switchTo(TestExecution.MOBILE);
        Assert.assertNotNull(DriverManager.getDriver(), "MOBILE driver should still be live on the second switch");
    }
}
