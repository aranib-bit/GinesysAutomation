package com.gsl.testdata;

/**
 * Partitions a {@link UserCredential} into one of the two execution models {@link UserPool}
 * understands, driving how it is stored, acquired, and released.
 * <ul>
 *   <li>{@code SET1} — an independent-test credential. Held in {@link UserPool}'s per-type
 *   queue/semaphore pair; one thread acquires it exclusively via
 *   {@link UserPool#acquireSet1User(UserType, String)} and releases it in
 *   {@code @AfterMethod} via {@link UserPool#releaseSet1User(UserCredential)}.</li>
 *   <li>{@code SET2} — a concurrent/synchronized-test credential, grouped by
 *   {@link UserCredential#groupId()} and handed out as a whole group via
 *   {@link UserPool#getSet2Group(String)} for tests that coordinate multiple threads
 *   (e.g. via a {@code CyclicBarrier}) against a fixed user list.</li>
 * </ul>
 * This is the enum actually read at runtime for SET1/SET2 partitioning — see
 * {@link UserCredentialLoader}, {@link UserPool}, and {@code HeartbeatListener}. It is
 * unrelated to the similarly-named but currently uncalled {@code com.gsl.enums.ExecutionType}.
 */
public enum ExecutionSet {
    SET1, SET2
}
