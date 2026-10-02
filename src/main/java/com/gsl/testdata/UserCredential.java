package com.gsl.testdata;

/**
 * Immutable credential snapshot assigned to one test thread — the unit {@link UserPool} hands out
 * and {@link UserCredentialLoader} produces from {@code UsersList.xlsx}.
 * Stored in {@code DriverManager.currentUser} (UI) and {@code DriverManager.apiUser} (API)
 * ThreadLocals for the duration of a test/session.
 *
 * @param groupId {@code null} for {@link ExecutionSet#SET1}; a shared value (e.g. "G1"/"G2") for
 *                {@link ExecutionSet#SET2} credentials that must be handed out together as one
 *                group via {@link UserPool#getSet2Group(String)}.
 */
public record UserCredential(
        String username,
        String password,
        UserType userType,
        ExecutionSet executionSet,
        String groupId
) {}
