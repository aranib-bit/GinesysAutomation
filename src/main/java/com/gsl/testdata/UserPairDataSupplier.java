package com.gsl.testdata;

import com.gsl.utils.Log;
import io.github.sskorol.core.DataSupplier;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * TestDataSupplier-based DataSupplier returning paired (UIUser, APIUser) rows for SET-1 parallel tests.
 * Each row drives one test invocation on its own thread with a unique credential pair.
 *
 * Usage:
 *   @Test(dataProvider = "set1UserPairs", dataProviderClass = UserPairDataSupplier.class)
 *   public void myTest(UserCredential uiUser, UserCredential apiUser) { ... }
 *
 * WebTest.setUp() auto-detects these params via extractCredential() and skips UserPool acquisition.
 *
 * TODO-confirm: no test class in this codebase currently declares
 * {@code dataProviderClass = UserPairDataSupplier.class}/{@code dataProvider = "set1UserPairs"} —
 * a repo-wide search found only this declaration. Do not assume it is dead code without checking
 * for newer callers first; it may be prepared for an upcoming SET1-paired-user test.
 */
public class UserPairDataSupplier {

    @DataSupplier(runInParallel = true, name = "set1UserPairs")
    public Stream<Object[]> set1UserPairs() {
        List<UserCredential> all = UserCredentialLoader.loadAll();
        List<UserCredential> uiUsers = all.stream()
                .filter(u -> u.executionSet() == ExecutionSet.SET1 && u.has(UserType.UI))
                .toList();
        List<UserCredential> apiUsers = all.stream()
                .filter(u -> u.executionSet() == ExecutionSet.SET1 && u.has(UserType.WEBAPI))
                .toList();
        int count = Math.min(uiUsers.size(), apiUsers.size());
        if (count == 0) {
            throw new IllegalStateException(
                    "[UserPairDataSupplier] No SET1 user pairs available. " +
                    "Ensure UsersList.xlsx has matching UI + API rows with executionSet=SET1.");
        }
        Log.info("[UserPairDataSupplier] Providing {} SET1 user pair(s).", count);
        return IntStream.range(0, count)
                .mapToObj(i -> new Object[]{uiUsers.get(i), apiUsers.get(i)});
    }
}
