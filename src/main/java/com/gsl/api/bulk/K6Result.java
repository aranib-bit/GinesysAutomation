package com.gsl.api.bulk;

import lombok.Value;

import java.util.List;

/** The outcome of one k6 run: exit code and status, the {@code K6_SUMMARY created=N failed=M} totals when the scenario printed them (else -1), and the output tail. */
@Value
public class K6Result {
    int exitCode;
    K6Status status;
    int created;
    int failed;
    List<String> outputTail;

    public boolean passed() {
        return status == K6Status.PASSED;
    }

    public boolean hasSummary() {
        return created >= 0;
    }
}
