package com.gsl.reports;

import com.gsl.reports.profile.RuntimeExecutionProfile;

public final class RuntimeProfileHolder {

    private static volatile RuntimeExecutionProfile profile;

    private RuntimeProfileHolder() {
    }

    public static void set(RuntimeExecutionProfile p) {
        profile = p;
    }

    public static RuntimeExecutionProfile get() {
        return profile;
    }
}
