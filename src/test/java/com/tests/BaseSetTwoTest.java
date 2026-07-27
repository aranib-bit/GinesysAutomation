package com.tests;

import com.gsl.concurrent.ConcurrentResultManager;
import com.gsl.concurrent.ConcurrentSyncManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.driver.Driver;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for SET-2 concurrent tests.
 * Two CyclicBarriers (beforeSave, afterSave) are shared across all threads in a group.
 * Each thread receives a deterministic user from the group list based on its arrival index.
 *
 * TestNG configuration requirement:
 *   <parameter name="set2.group.id" value="G1"/>
 *   thread-count must equal the number of users in the group.
 */
public abstract class BaseSetTwoTest extends WebTest {

    private static final ConcurrentHashMap<String, CyclicBarrier>  BEFORE_SAVE_BARRIERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CyclicBarrier>  AFTER_SAVE_BARRIERS  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger>  GROUP_COUNTERS       = new ConcurrentHashMap<>();

    protected String groupId;

    /** Prevent WebTest.setUp(Method, Object[]) from running — BaseSetTwoTest owns full setup via setUp(Method, String). */
    @Override
    @BeforeMethod(enabled = false)
    public void setUp(Method method, Object[] testParams) { /* disabled */ }

    @BeforeMethod
    @Parameters("set2.group.id")
    public void setUp(Method method, @Optional("G1") String groupId) {
        this.groupId = groupId;
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        List<UserCredential> groupUsers = UserPool.getSet2Group(groupId);
        int parties = groupUsers.size();

        // Ensure barriers and counter exist for this group (created once, reused by all threads)
        BEFORE_SAVE_BARRIERS.computeIfAbsent(groupId, k ->
                ConcurrentSyncManager.createBarrier(parties, groupId + ".beforeSave"));
        AFTER_SAVE_BARRIERS.computeIfAbsent(groupId, k ->
                ConcurrentSyncManager.createBarrier(parties, groupId + ".afterSave"));
        AtomicInteger counter = GROUP_COUNTERS.computeIfAbsent(groupId, k -> new AtomicInteger(0));

        // Assign user by arrival order — deterministic within the group
        int index = counter.getAndIncrement() % parties;
        UserCredential uiUser = groupUsers.get(index);
        DriverManager.setCurrentUser(uiUser);
        TestContextManager.setUiUser(uiUser.username());

        Log.info("[BaseSetTwoTest] thread={} groupId={} index={} user={}",
                Thread.currentThread().getId(), groupId, index, uiUser.username());

        if (index == 0) {
            ConcurrentResultManager.reset();
        }
        Driver.initDriver();
    }

    /**
     * Call this after filling the form, before clicking Save.
     *
     * Extension point for future SET-2 test classes that want {@code RecoveryManager} on the
     * failure path: if a test's own action between rendezvous points throws, classify it via
     * {@link com.gsl.driver.FailureClassifier} first. For a {@code NAVIGATION_FAILURE}/
     * {@code SESSION_FAILURE}/{@code BROWSER_FAILURE} here, do NOT attempt Recovery Levels 3-4 —
     * the other participant(s) are already blocked waiting on this same barrier, and trying to
     * silently recover and rejoin would either miss the checkpoint window or falsify the
     * race-condition scenario the test exists to prove. Let the barrier break naturally (it
     * correctly fails the other participant too via {@code BrokenBarrierException}) instead.
     * Recovery Levels 1-2 (a fast retry/refresh) are fine *before* a barrier checkpoint, as long
     * as they complete well inside {@code ExecutionConfig.syncTimeoutSeconds()} (default 30s).
     * Not wired in here since no concrete SET-2 test class exists yet to validate against.
     */
    protected void awaitBeforeSave() {
        ConcurrentSyncManager.await(BEFORE_SAVE_BARRIERS.get(groupId), groupId + ".beforeSave");
    }

    /** Call this after clicking Save, before asserting results. See {@link #awaitBeforeSave()}. */
    protected void awaitAfterSave() {
        ConcurrentSyncManager.await(AFTER_SAVE_BARRIERS.get(groupId), groupId + ".afterSave");
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        // Release reserved documents BEFORE quitting the driver so the log line correlates
        // with the still-active thread context. Without this, SET-2 leaks reservations across
        // group runs (the parent WebTest.tearDown is bypassed by this override).
        try {
            DocumentReservationRegistry.releaseCurrentThread();
        } catch (Exception e) {
            Log.warn("[BaseSetTwoTest] DocumentReservationRegistry.releaseCurrentThread threw: {}",
                    e.getMessage());
        }
        try {
            Driver.quitDriver();
        } catch (Exception e) {
            Log.warn("[BaseSetTwoTest] Driver.quitDriver threw: {}", e.getMessage());
        }
        DriverManager.unloadCurrentUser();
        TestContextManager.clear();
    }

    /**
     * Validates that exactly one concurrent user saved successfully and exactly one received a conflict.
     * Runs after all test methods in the class complete.
     * Subclasses must call ConcurrentResultManager.recordSuccess/recordFailure inside their test methods.
     */
    @AfterClass
    public void validateConcurrentOutcome() {
        int successes = ConcurrentResultManager.getSuccessCount();
        int failures  = ConcurrentResultManager.getFailureCount();
        Log.info("[BaseSetTwoTest] Concurrent outcome — success={} failed={} successUsers={} failedUsers={}",
                successes, failures,
                ConcurrentResultManager.getSuccessUsers(),
                ConcurrentResultManager.getFailedUsers());
        Assert.assertEquals(successes, 1,
                "Exactly one concurrent user should save successfully. successUsers=" +
                ConcurrentResultManager.getSuccessUsers());
        Assert.assertEquals(failures, 1,
                "Exactly one concurrent user should receive a conflict warning. failedUsers=" +
                ConcurrentResultManager.getFailedUsers());
    }
}
