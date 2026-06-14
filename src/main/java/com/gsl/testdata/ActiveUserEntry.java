package com.gsl.testdata;

/**
 * Snapshot of which test and thread currently holds a SET-1 user credential.
 * Stored in UserPool's active-user map; removed on release.
 */
public record ActiveUserEntry(String testName, String threadName, long startMs) {}
