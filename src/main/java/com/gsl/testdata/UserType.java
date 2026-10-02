package com.gsl.testdata;

/**
 * The two credential kinds {@link UserPool} manages as separate queues/semaphores: {@code UI}
 * for browser-driven Selenium tests and {@code API} for RestAssured/WebAPI-style tests. A
 * {@link UserCredential} is tagged with exactly one, and {@link UserPool#hasUsersOfType(UserType)}
 * lets a caller check whether any of a given type were configured before blocking on
 * {@link UserPool#acquireSet1User(UserType, String)}.
 */
public enum UserType {
    UI, API
}
