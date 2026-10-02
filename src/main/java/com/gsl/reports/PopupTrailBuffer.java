package com.gsl.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local list that records every popup detected during a test, including those
 * handled by page flow (CONFIRM dialogs). Cleared at test start by TestListener.
 *
 * <p>Used by ActivePopupSection to show the full popup history, not just what is
 * visible on screen at the moment diagnostics are collected.
 */
public final class PopupTrailBuffer {

    private static final ThreadLocal<List<String>> TRAIL =
            ThreadLocal.withInitial(ArrayList::new);

    private PopupTrailBuffer() {}

    /** Appends {@code entry} to this thread's popup trail; ignores null/blank entries. */
    public static void record(String entry) {
        if (entry == null || entry.isBlank()) return;
        TRAIL.get().add(entry);
    }

    /** Returns a copy of this thread's full popup trail (oldest first). */
    public static List<String> getAll() {
        return new ArrayList<>(TRAIL.get());
    }

    /** Clears this thread's popup trail — called at the start of each test. */
    public static void clear() {
        TRAIL.get().clear();
        TRAIL.remove();
    }
}
