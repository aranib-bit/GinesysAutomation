package com.tests.API;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.request.approval.GlobalUserListRequest;
import com.gsl.approval.api.ApiUserDirectoryGateway;
import com.gsl.approval.auth.ApprovalAuthenticationException;
import com.gsl.approval.auth.ApproverCredentialSource;
import com.gsl.approval.auth.ApproverLoginStrategy;
import com.gsl.approval.auth.ApproverSession;
import com.gsl.approval.auth.ApproverSessionService;
import com.gsl.approval.auth.LiveSessionSource;
import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApproverIdentity;
import com.gsl.approval.model.DirectoryPage;
import com.gsl.approval.model.DirectoryUser;
import com.gsl.approval.service.ApprovalAssignmentService;
import com.gsl.approval.service.ApprovalConfigurationException;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;
import com.gsl.approval.service.UserDirectory;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserType;
import com.gsl.testdata.ExecutionSet;
import com.tests.API.support.StubApiServer;
import com.tests.API.support.WireAssert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 4 of the approval workflow: who can approve a level (named users and role members read from the paged user directory) and how a
 * session acting as that approver is obtained (reuse a live one, otherwise log in; never log anyone out up front).
 */
public class ApprovalAssignmentAndSessionUnitTest {

    private StubApiServer server;

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        server.reset();
    }

    // ---- directory -------------------------------------------------------------------------------------------------------------------------

    private static DirectoryUser user(int code, String name, int role, boolean extinct) {
        return new DirectoryUser(code, name, name.toUpperCase(), role, "Role" + role, extinct);
    }

    /** A fake directory served in pages of {@code size}; counts the pages asked for. */
    private static final class PagedFake implements com.gsl.approval.service.UserDirectoryGateway {
        final List<DirectoryUser> all;
        final AtomicInteger pagesRead = new AtomicInteger();
        final boolean truncate;

        PagedFake(List<DirectoryUser> all, boolean truncate) {
            this.all = all;
            this.truncate = truncate;
        }

        @Override
        public DirectoryPage fetchPage(int page, int limit) {
            pagesRead.incrementAndGet();
            int from = (page - 1) * limit;
            if (truncate && page > 1) {
                return new DirectoryPage(all.size(), List.of());
            }
            List<DirectoryUser> slice = from >= all.size() ? List.of() : all.subList(from, Math.min(all.size(), from + limit));
            return new DirectoryPage(all.size(), slice);
        }
    }

    private static List<DirectoryUser> fiveUsers() {
        return List.of(user(1, "a", 10, false), user(2, "b", 10, false), user(3, "c", 20, false), user(4, "d", 10, true), user(5, "e", 10, false));
    }

    @Test
    public void directoryReadsEveryPageNotJustTheFirst() {
        PagedFake fake = new PagedFake(fiveUsers(), false);
        UserDirectory directory = new UserDirectory(fake, 2);

        assertEquals(directory.activeUsersOfRole(10).stream().map(DirectoryUser::getUserCode).toList(), List.of(1, 2, 5));
        assertEquals(fake.pagesRead.get(), 3, "5 users at 2 per page = 3 pages");
        assertTrue(directory.byUserCode(5).isPresent(), "a user on the last page is found");
    }

    @Test
    public void directoryIsReadOncePerInstance() {
        PagedFake fake = new PagedFake(fiveUsers(), false);
        UserDirectory directory = new UserDirectory(fake, 2);
        directory.byUserCode(1);
        directory.activeUsersOfRole(20);
        assertEquals(fake.pagesRead.get(), 3);
    }

    @Test
    public void incompleteDirectoryIsAFailureNotAShortList() {
        UserDirectory directory = new UserDirectory(new PagedFake(fiveUsers(), true), 2);
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> directory.byUserCode(1));
        assertEquals(e.getKind(), Kind.INVALID_RESPONSE);
    }

    @Test
    public void directoryGatewayMapsTheLoginNameFromTheAbbreviationAndNeverReadsThePassword() {
        server.respond("POST", "/GlobalUser/GetListOfGlobalAppEmpUsers", 200, WireAssert.sample("approval/global-user-list-trimmed.json"));
        ApiUserDirectoryGateway gateway = new ApiUserDirectoryGateway(new ApprovalApiClient(server.spec()), "1", 1, "2026-11-03T00:00:00");

        DirectoryPage page = gateway.fetchPage(1, 50);

        assertEquals(page.getTotalCount(), 91, "the screen's own total, not the number of rows in this sample");
        assertEquals(page.getUsers().size(), 5);
        DirectoryUser bt = page.getUsers().get(4);
        assertEquals(bt.getUserCode(), 51009);
        assertEquals(bt.getUsername(), "bt", "the login is the abbreviation");
        assertEquals(bt.getEmployeeNo(), "7", "the employee number is kept for display only");
        assertFalse(bt.isExtinct());
        assertEquals(bt.toIdentity().getUsername(), "bt");

        DirectoryUser hena = page.getUsers().get(0);
        assertEquals(hena.getUsername(), "hena2");
        assertEquals(hena.getEmployeeNo(), "007");

        DirectoryUser extinct = page.getUsers().get(1);
        assertEquals(extinct.getUsername(), "ssaha", "an e-mail employee number is never taken as the login");
        assertEquals(extinct.getEmployeeNo(), "sayandip.s@gsl.in");
        assertTrue(extinct.isExtinct());

        assertFalse(page.getUsers().toString().contains("not-a-real-password"), "the directory password is never mapped");
        assertTrue(server.last().body().contains("\"page\":1"), server.last().body());
    }

    @Test
    public void aRowWithoutAnAbbreviationFallsBackToTheEmployeeNumber() {
        server.respond("POST", "/GlobalUser/GetListOfGlobalAppEmpUsers", 200,
                "{\"success\":true,\"result\":{\"totalCount\":2,\"items\":["
                        + "{\"ecode\":5.0,\"eno\":\"oldstyle\",\"abbre\":null,\"name\":\"Old Style\",\"rlcode\":1.0,\"ext\":\"N\"},"
                        + "{\"ecode\":6.0,\"eno\":\"x\",\"abbre\":\"  \",\"name\":\"Blank\",\"rlcode\":1.0,\"ext\":\"N\"}]}}");

        List<DirectoryUser> users = new ApiUserDirectoryGateway(new ApprovalApiClient(server.spec()), "1", 1, "d").fetchPage(1, 50).getUsers();

        assertEquals(users.get(0).getUsername(), "oldstyle");
        assertEquals(users.get(1).getUsername(), "x");
    }

    @Test
    public void aRoleOnA91UserDirectoryIsExpandedAcrossBothPages() {
        List<DirectoryUser> all = new ArrayList<>();
        for (int i = 1; i <= 91; i++) {
            // every third user holds the role, so members are spread over page 1 (users 1-50) and page 2 (users 51-91)
            all.add(new DirectoryUser(1000 + i, "login" + i, "User " + i, i % 3 == 0 ? 50996 : 1055, "r", i == 90, "no" + i));
        }
        PagedFake fake = new PagedFake(all, false);
        UserDirectory directory = new UserDirectory(fake, 50);

        List<DirectoryUser> members = directory.activeUsersOfRole(50996);

        assertEquals(fake.pagesRead.get(), 2, "91 users at 50 per page = 2 pages");
        assertEquals(members.size(), 29, "30 members (3, 6, ... 90) minus the extinct user 90");
    }

    @Test
    public void directoryRequestMatchesTheHar() {
        WireAssert.sameJson(GlobalUserListRequest.page("1", 1, "2026-11-03T00:00:00", 1, 50), "approval/global-user-list-request.json");
    }

    @Test
    public void laterPagesAskForTheRightStartOffset() {
        assertEquals(GlobalUserListRequest.page("1", 1, "d", 3, 50).getStart(), 100);
    }

    // ---- assignment ------------------------------------------------------------------------------------------------------------------------

    private static ApprovalApprover userSlot(int code) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.USER).userCode(code).name("u" + code).build();
    }

    private static ApprovalApprover roleSlot(int roleId) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.ROLE).roleId(roleId).build();
    }

    private static ApprovalLevel level(ApprovalLevel.Quorum quorum, Integer count, ApprovalApprover... slots) {
        return ApprovalLevel.builder().levelNo(1).quorum(quorum).quorumCount(count).approvers(List.of(slots)).build();
    }

    private ApprovalAssignmentService assignment() {
        return new ApprovalAssignmentService(new UserDirectory(new PagedFake(fiveUsers(), false), 2));
    }

    @Test
    public void namedUsersResolveToTheirLoginNames() {
        List<ApproverIdentity> who = assignment().resolveApprovers(level(ApprovalLevel.Quorum.ALL, null, userSlot(1), userSlot(3)));
        assertEquals(who.stream().map(ApproverIdentity::getUsername).toList(), List.of("a", "c"));
    }

    @Test
    public void roleExpandsToItsActiveUsersAcrossPages() {
        List<ApproverIdentity> who = assignment().resolveApprovers(level(ApprovalLevel.Quorum.CUSTOM, 1, roleSlot(10)));
        assertEquals(who.stream().map(ApproverIdentity::getUserCode).toList(), List.of(1, 2, 5), "extinct user 4 is left out");
    }

    @Test
    public void aUserNamedAndInTheRoleIsListedOnce() {
        List<ApproverIdentity> who = assignment().resolveApprovers(level(ApprovalLevel.Quorum.CUSTOM, 1, userSlot(2), roleSlot(10)));
        assertEquals(who.stream().map(ApproverIdentity::getUserCode).toList(), List.of(2, 1, 5));
    }

    @Test
    public void extinctSlotAndExtinctUserAreSkipped() {
        ApprovalApprover extinctSlot = ApprovalApprover.builder().type(ApprovalApprover.Type.USER).userCode(1).extinct(true).build();
        assertEquals(assignment().resolveApprovers(level(ApprovalLevel.Quorum.CUSTOM, 1, extinctSlot, userSlot(2), userSlot(4))).size(), 1);
    }

    @Test
    public void userMissingFromTheDirectoryIsNotFound() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> assignment().resolveApprovers(level(ApprovalLevel.Quorum.ALL, null, userSlot(777))));
        assertEquals(e.getKind(), Kind.NOT_FOUND);
    }

    @Test
    public void tooFewActiveApproversForTheQuorumIsAConfigurationError() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> assignment().resolveApprovers(level(ApprovalLevel.Quorum.CUSTOM, 2, roleSlot(20))));
        assertEquals(e.getKind(), Kind.INVALID_CONFIGURATION);
    }

    // ---- sessions --------------------------------------------------------------------------------------------------------------------------

    private static ApproverIdentity who(int code, String name) {
        return new ApproverIdentity(code, name, name);
    }

    private static UserCredential cred(String name) {
        return new UserCredential(name, "pw-" + name, UserType.UI, ExecutionSet.SET1, null);
    }

    private static final class Fixture {
        final List<String> loggedIn = new ArrayList<>();
        final List<String> closed = new ArrayList<>();
        final List<Integer> liveCodes = new ArrayList<>();
        final List<String> withCredentials = new ArrayList<>();
        final List<String> loginFails = new ArrayList<>();
        final AtomicInteger liveLookups = new AtomicInteger();

        LiveSessionSource live() {
            return approver -> {
                liveLookups.incrementAndGet();
                return liveCodes.contains(approver.getUserCode())
                        ? Optional.of(new ApproverSession(approver, null, ApproverSession.Source.BROWSER_TOKENS, () -> closed.add("LIVE-MUST-NOT-CLOSE")))
                        : Optional.empty();
            };
        }

        ApproverCredentialSource credentials() {
            return approver -> withCredentials.contains(approver.getUsername()) ? Optional.of(cred(approver.getUsername())) : Optional.empty();
        }

        ApproverLoginStrategy login() {
            return (approver, credential) -> {
                if (loginFails.contains(approver.getUsername())) {
                    throw new ApprovalAuthenticationException("refused");
                }
                loggedIn.add(approver.getUsername());
                return new ApproverSession(approver, null, ApproverSession.Source.NEW_LOGIN, () -> closed.add(approver.getUsername()));
            };
        }

        ApproverSessionService service() {
            return new ApproverSessionService(List.of(live()), credentials(), login());
        }
    }

    @Test
    public void aLiveSessionIsReusedWithoutLoggingIn() {
        Fixture f = new Fixture();
        f.liveCodes.add(51009);
        f.withCredentials.add("bt");

        ApproverSession s = f.service().acquire(who(51009, "bt"));

        assertTrue(s.reused());
        assertEquals(s.source(), ApproverSession.Source.BROWSER_TOKENS);
        assertTrue(f.loggedIn.isEmpty(), "no login when the approver is already logged in");
    }

    @Test
    public void withoutALiveSessionTheApproverIsLoggedInAndLaterClosed() {
        Fixture f = new Fixture();
        f.withCredentials.add("bt");
        ApproverSessionService service = f.service();

        ApproverSession s = service.acquire(who(51009, "bt"));

        assertFalse(s.reused());
        assertEquals(f.loggedIn, List.of("bt"));
        service.releaseAll();
        assertEquals(f.closed, List.of("bt"));
    }

    @Test
    public void releasingNeverClosesAReusedSession() {
        Fixture f = new Fixture();
        f.liveCodes.add(51009);
        ApproverSessionService service = f.service();
        service.acquire(who(51009, "bt")).close();
        service.releaseAll();
        assertTrue(f.closed.isEmpty());
    }

    @Test
    public void approverWithoutCredentialsFailsClearlyAndDoesNotLogAnyoneIn() {
        Fixture f = new Fixture();
        ApprovalAuthenticationException e = expectThrows(ApprovalAuthenticationException.class, () -> f.service().acquire(who(155061, "aritra")));
        assertTrue(e.getMessage().contains("aritra"), e.getMessage());
        assertTrue(e.getMessage().contains("155061"), "the user code is named too: " + e.getMessage());
        assertFalse(e.getMessage().contains("pw-"), "no password in the message");
        assertTrue(f.loggedIn.isEmpty());
    }

    @Test
    public void firstAvailablePrefersALiveSessionOverALogin() {
        Fixture f = new Fixture();
        f.withCredentials.addAll(List.of("a", "b"));
        f.liveCodes.add(2);

        ApproverSession s = f.service().acquireFirstAvailable(List.of(who(1, "a"), who(2, "b")));

        assertEquals(s.identity().getUserCode(), 2);
        assertTrue(f.loggedIn.isEmpty());
    }

    @Test
    public void firstAvailableSkipsUsersWithoutCredentialsAndFailedLogins() {
        Fixture f = new Fixture();
        f.withCredentials.addAll(List.of("b", "c"));
        f.loginFails.add("b");

        ApproverSession s = f.service().acquireFirstAvailable(List.of(who(1, "a"), who(2, "b"), who(3, "c")));

        assertEquals(s.identity().getUsername(), "c");
    }

    @Test
    public void firstAvailableFailsWhenNobodyCanBeUsed() {
        Fixture f = new Fixture();
        expectThrows(ApprovalAuthenticationException.class, () -> f.service().acquireFirstAvailable(List.of(who(1, "a"), who(2, "b"))));
    }

    @Test
    public void sessionToStringShowsNoTokens() {
        ApproverSession s = new ApproverSession(who(1, "bt"), null, ApproverSession.Source.NEW_LOGIN, null);
        assertNotNull(s.toString());
        assertTrue(s.toString().contains("tokens=***"));
        assertSame(s.identity().getUsername(), "bt");
    }
}
