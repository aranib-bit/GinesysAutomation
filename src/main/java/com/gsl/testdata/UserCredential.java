package com.gsl.testdata;

/**
 * Immutable credential snapshot assigned to one test thread.
 * Stored in DriverManager.currentUser (UI) and DriverManager.apiUser (API) ThreadLocals.
 * groupId is null for SET1; "G1"/"G2" etc. for SET2 concurrent groups.
 */
public record UserCredential(
        String username,
        String password,
        UserType userType,
        ExecutionSet executionSet,
        String groupId
) {}
