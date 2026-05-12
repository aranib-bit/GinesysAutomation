package com.gsl.enums;

/** Identifies the remote execution target — used by WebDriverData for remote factory routing. */
public enum RemoteModeType {
    GRID,
    DOCKER_GRID,
    BROWSERSTACK,
    LAMBDATEST,
    SELENOID
}
