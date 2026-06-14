package com.gsl.exceptions;

/**
 * Thrown by UserPool.acquireSet1User() when no user becomes available within
 * pool.acquire.timeout.seconds. The message includes a full snapshot of which
 * tests are currently holding users so the cause of starvation is immediately visible.
 */
public class UserPoolAcquisitionTimeoutException extends RuntimeException {

    public UserPoolAcquisitionTimeoutException(String message) {
        super(message);
    }
}
