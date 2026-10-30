package com.gsl.api.bulk;

/**
 * What a k6 process exit code means. k6 does not use one failure code: a crossed threshold (99) is a measured result, while a script error
 * (107/108) or a timeout (100-102) means the run itself did not do its job, and callers treat those differently.
 */
public enum K6Status {
    PASSED,
    THRESHOLDS_FAILED,
    SCRIPT_ERROR,
    TIMED_OUT,
    OTHER_FAILURE;

    /** k6's documented exit codes (go.k6.io/k6/errext/exitcodes); anything else non-zero is {@link #OTHER_FAILURE}. */
    public static K6Status fromExitCode(int exitCode) {
        return switch (exitCode) {
            case 0 -> PASSED;
            case 99 -> THRESHOLDS_FAILED;
            case 100, 101, 102 -> TIMED_OUT;
            case 107, 108 -> SCRIPT_ERROR;
            default -> OTHER_FAILURE;
        };
    }
}
