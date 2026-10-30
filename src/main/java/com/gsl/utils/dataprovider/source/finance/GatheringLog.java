package com.gsl.utils.dataprovider.source.finance;

import java.util.ArrayList;
import java.util.List;

/**
 * The framed "API DATA GATHERING - ..." log every API data source writes while it resolves references (shown in the report's data
 * gathering node). One place for the frame, instead of each source repeating the banner lines.
 */
public final class GatheringLog {

    private static final String BAR = "═".repeat(59);

    private GatheringLog() {}

    /** A new log that starts with the banner and the (indented) {@code title}. */
    public static List<String> start(String title) {
        List<String> log = new ArrayList<>();
        log.add(BAR);
        log.add("  " + title);
        log.add(BAR);
        return log;
    }

    /** Closes the log with the banner line. */
    public static void end(List<String> log) {
        log.add(BAR);
    }
}
