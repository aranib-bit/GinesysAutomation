package com.tests.API;

import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.LoginType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** A [UI, WEBAPI] user is one login: counted in both pools, held for one use at a time, and kept free for WEBAPI when a UI-only user is available. */
public class UserPoolSharedUserUnitTest {

    private static UserCredential user(String name, UserType... types) {
        return new UserCredential(name, "pw", Set.of(types), LoginType.ERP, ExecutionSet.SET1, null);
    }

    @AfterMethod(alwaysRun = true)
    public void clearPool() {
        UserPool.initialize(List.of());
    }

    @Test
    public void aSharedUserIsCountedInBothPools() {
        UserPool.initialize(List.of(user("a", UserType.UI, UserType.WEBAPI), user("b", UserType.UI), user("c", UserType.WEBAPI), user("p", UserType.PERF)));
        assertEquals(UserPool.initialCountOf(UserType.UI), 2);
        assertEquals(UserPool.initialCountOf(UserType.WEBAPI), 2);
        assertEquals(UserPool.initialCountOf(UserType.PERF), 1);
        assertEquals(UserPool.allUsernamesOfType(UserType.UI), List.of("a", "b"));
    }

    @Test
    public void aUiAcquireTakesAUiOnlyUserBeforeASharedOne() {
        UserPool.initialize(List.of(user("shared", UserType.UI, UserType.WEBAPI), user("uionly", UserType.UI)));
        UserCredential first = UserPool.acquireSet1User(UserType.UI, "t1");
        assertEquals(first.username(), "uionly", "the shared login stays free for WEBAPI as long as a UI-only user exists");
        UserCredential second = UserPool.acquireSet1User(UserType.UI, "t2");
        assertEquals(second.username(), "shared");
        UserPool.releaseSet1User(first);
        UserPool.releaseSet1User(second);
    }

    @Test
    public void aSharedUserHeldForUiCannotBeAcquiredForWebapiUntilReleased() throws Exception {
        UserPool.initialize(List.of(user("shared", UserType.UI, UserType.WEBAPI)));
        UserCredential heldForUi = UserPool.acquireSet1User(UserType.UI, "ui-test");

        CompletableFuture<UserCredential> webapi = CompletableFuture.supplyAsync(() -> UserPool.acquireSet1User(UserType.WEBAPI, "api-session"));
        expectThrows(TimeoutException.class, () -> webapi.get(1500, TimeUnit.MILLISECONDS));
        assertFalse(webapi.isDone(), "the same login must not be handed to a WEBAPI session while a UI test holds it");

        UserPool.releaseSet1User(heldForUi);
        UserCredential gotForApi = webapi.get(5, TimeUnit.SECONDS);
        assertEquals(gotForApi.username(), "shared");
        UserPool.releaseSet1User(gotForApi);
    }

    @Test
    public void theReverseHoldsToo() throws Exception {
        UserPool.initialize(List.of(user("shared", UserType.UI, UserType.WEBAPI)));
        UserCredential heldForApi = UserPool.acquireSet1User(UserType.WEBAPI, "api-session");
        CompletableFuture<UserCredential> ui = CompletableFuture.supplyAsync(() -> UserPool.acquireSet1User(UserType.UI, "ui-test"));
        expectThrows(TimeoutException.class, () -> ui.get(1500, TimeUnit.MILLISECONDS));
        UserPool.releaseSet1User(heldForApi);
        UserPool.releaseSet1User(ui.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void aDoubleReleaseDoesNotHandTheSameLoginOutTwice() {
        UserPool.initialize(List.of(user("only", UserType.UI)));
        UserCredential u = UserPool.acquireSet1User(UserType.UI, "t");
        UserPool.releaseSet1User(u);
        UserPool.releaseSet1User(u);
        UserCredential again = UserPool.acquireSet1User(UserType.UI, "t2");
        assertEquals(again.username(), "only");
        CompletableFuture<UserCredential> second = CompletableFuture.supplyAsync(() -> UserPool.acquireSet1User(UserType.UI, "t3"));
        expectThrows(TimeoutException.class, () -> second.get(1200, TimeUnit.MILLISECONDS));
        UserPool.releaseSet1User(again);
        UserPool.releaseSet1User(second.join());
    }

    @Test
    public void webapiSessionsFallBackToUiUsersOnlyWhenNoWebapiUserIsConfigured() {
        UserPool.initialize(List.of(user("ui1", UserType.UI)));
        UserCredential legacy = UserPool.acquireForWebApi("api");
        assertEquals(legacy.username(), "ui1");
        UserPool.releaseSet1User(legacy);

        UserPool.initialize(List.of(user("ui1", UserType.UI), user("api1", UserType.UI, UserType.WEBAPI)));
        UserCredential configured = UserPool.acquireForWebApi("api");
        assertEquals(configured.username(), "api1", "a UI-only user is never used for WEBAPI once WEBAPI users exist");
        assertNotEquals(configured.username(), "ui1");
        UserPool.releaseSet1User(configured);
    }

    @Test
    public void aTypeWithNoUsersSkipsInsteadOfHanging() {
        UserPool.initialize(List.of(user("ui1", UserType.UI)));
        assertFalse(UserPool.hasUsersOfType(UserType.PERF));
        expectThrows(org.testng.SkipException.class, () -> UserPool.acquireSet1User(UserType.PERF, "x"));
        assertTrue(UserPool.hasUsersOfType(UserType.UI));
    }
}