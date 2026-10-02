package com.gsl.exceptions;

/**
 * Thrown by UserPool.acquireSet1User() when no user becomes available within
 * pool.acquire.timeout.seconds. The message includes a full snapshot of which
 * tests are currently holding users so the cause of starvation is immediately visible.
 */
public class UserPoolAcquisitionTimeoutException extends RuntimeException {

    /** @param message the pre-formatted failure message, including the snapshot of tests currently holding users */
    public UserPoolAcquisitionTimeoutException(String message) {
        super(message);
    }
}
