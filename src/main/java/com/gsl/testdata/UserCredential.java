package com.gsl.testdata;

import java.util.Set;

/**
 * Immutable credential snapshot assigned to one test thread — the unit {@link UserPool} hands out
 * and {@link UserCredentialLoader} produces from {@code UsersList.xlsx}.
 * Stored in {@code DriverManager.currentUser} (UI) and {@code DriverManager.apiUser} (API)
 * ThreadLocals for the duration of a test/session.
 *
 * @param types     what the account is used for; one type, or exactly {@code {UI, WEBAPI}} (the only allowed combination, a shared login)
 * @param loginType how the account signs in
 * @param groupId   {@code null} for {@link ExecutionSet#SET1}; a shared value (e.g. "G1"/"G2") for
 *                  {@link ExecutionSet#SET2} credentials that must be handed out together as one
 *                  group via {@link UserPool#getSet2Group(String)}.
 */
public record UserCredential(
        String username,
        String password,
        Set<UserType> types,
        LoginType loginType,
        ExecutionSet executionSet,
        String groupId
) {

    public UserCredential {
        types = Set.copyOf(types);
        if (types.isEmpty()) throw new IllegalArgumentException("A user needs at least one type: " + username);
        if (loginType == null) loginType = LoginType.ERP;
    }

    /** A single-type ERP credential, the shape every row had before the {@code usertype}/{@code logintype} columns existed. */
    public UserCredential(String username, String password, UserType type, ExecutionSet executionSet, String groupId) {
        this(username, password, Set.of(type), LoginType.ERP, executionSet, groupId);
    }

    public boolean has(UserType type) {
        return types.contains(type);
    }

    /** True for a {@code [UI, WEBAPI]} account: one login used both ways, so it can be held for only one of them at a time. */
    public boolean isShared() {
        return types.size() > 1;
    }
}
