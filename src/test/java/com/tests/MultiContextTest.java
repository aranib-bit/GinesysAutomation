package com.tests;

import com.gsl.api.core.ApiSessionManager;
import com.gsl.driver.ContextOrchestrator;
import com.gsl.driver.DriverManager;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.PlatformType;
import com.gsl.enums.TestExecution;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Base class for tests that genuinely need to cross execution contexts within one test method
 * (e.g. WEBAPI -> MOBILE -> WEB -> MOBILE, or a WEB test's data-shortfall backfill via WEBAPI).
 * Sits alongside {@link WebTest}/{@link APITest}/{@link MobileTest} — none of those are
 * modified; single-context tests keep using them exactly as today.
 *
 * <p>Credentials are acquired lazily, only for contexts actually visited via {@code switchTo()}
 * — a test that only ever calls {@code switchTo(TestExecution.API, ApiPlatform.WEBAPI)} never
 * touches the UI user pool <em>here</em>. WEBAPI's own credential is acquired and released
 * entirely by {@code ApiSessionFactory.createForWebApi()} (self-contained, draws a
 * {@code UserType.UI} credential — same account a browser login would use, since WEBAPI login
 * is the same account, not a separate API-type pool), so no separate acquisition happens in
 * this class for API context — doing so would double-acquire.
 */
public abstract class MultiContextTest extends BaseTest {

    private final ThreadLocal<Boolean> uiUserAcquired = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<String>  testLabel      = new ThreadLocal<>();

    @BeforeMethod
    public void setUp(Method method) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();
        testLabel.set(method.getDeclaringClass().getSimpleName() + "." + method.getName());
        // No eager acquisition — the WEB/MOBILE UI user is acquired lazily on first switchTo()
        // that needs it; WEBAPI acquires and releases its own credential independently.
    }

    protected void switchTo(TestExecution execution) {
        acquireIfNeeded(execution);
        ContextOrchestrator.switchTo(execution);
    }

    protected void switchTo(TestExecution execution, PlatformType explicitMobilePlatform) {
        acquireIfNeeded(execution);
        ContextOrchestrator.switchTo(execution, explicitMobilePlatform);
    }

    protected void switchTo(TestExecution execution, ApiPlatform apiPlatform) {
        // No credential acquisition here — ApiSessionFactory.createForWebApi() is fully
        // self-contained (acquires + releases its own UserType.UI credential); PUBLICAPI never
        // touches UserPool at all.
        ContextOrchestrator.switchTo(execution, apiPlatform);
    }

    private void acquireIfNeeded(TestExecution execution) {
        boolean needsUiUser = (execution == TestExecution.WEB || execution == TestExecution.MOBILE)
                && !uiUserAcquired.get();
        if (needsUiUser) {
            UserCredential uiUser = UserPool.acquireSet1User(UserType.UI, testLabel.get());
            DriverManager.setCurrentUser(uiUser);
            TestContextManager.setUiUser(uiUser.username());
            uiUserAcquired.set(true);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        long tid = Thread.currentThread().getId();
        try {
            for (PlatformType pt : PlatformType.values()) {
                WebDriver d = DriverManager.getDriverForContext(pt);
                if (d != null) {
                    try {
                        d.quit();
                    } catch (Exception e) {
                        Log.warn("[MultiContextTest] [T-{}] driver.quit() threw for context={}: {}", tid, pt, e.getMessage());
                    }
                }
            }
        } finally {
            DriverManager.unloadAllContexts();
            ApiSessionManager.unloadAllContexts(); // logs out + releases its own credential — see ApiSessionBundle.logout()

            if (uiUserAcquired.get()) {
                UserCredential ui = DriverManager.getCurrentUser();
                if (ui != null) UserPool.releaseSet1User(ui);
            }

            uiUserAcquired.remove();
            testLabel.remove();
            DriverManager.unloadCurrentUser();
            TestContextManager.clear();
        }
    }
}
