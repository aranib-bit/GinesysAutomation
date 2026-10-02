package com.gsl.validation;

import com.gsl.validation.ui.UiValidationFailure;

import java.util.ArrayList;
import java.util.List;

/**
 * The single error a flow throws at its final assertion point when one or more validation AREAS
 * (UI contract, database validation, snapshot validation, save-result assertions) failed — each
 * area runs to completion first, so one area's failure never hides another's.
 *
 * <p>{@link #getMessage()} is a readable multi-line summary (one block per area, first
 * {@value #MAX_LINES} entries each, then "+N more"), which {@code FailureSummaryRenderer} shows
 * as-is and {@code RootCauseClassifier} classifies from {@link #areas()}.
 */
public final class CombinedValidationError extends AssertionError {

    public static final int MAX_LINES = 3;

    public enum Kind { UI, DATABASE, SNAPSHOT, OTHER }

    /** One failed area: its kind, display title and the individual failure lines. */
    public record Area(Kind kind, String title, List<String> lines) {}

    private final List<Area> areas;

    public CombinedValidationError(List<Area> areas) {
        super(buildMessage(areas));
        this.areas = List.copyOf(areas);
    }

    public List<Area> areas() { return areas; }

    public boolean onlyUi() {
        return areas.stream().allMatch(a -> a.kind() == Kind.UI);
    }

    public boolean hasKind(Kind kind) {
        return areas.stream().anyMatch(a -> a.kind() == kind);
    }

    /** UI area from raw UI failures — each line is {@link UiValidationFailure#shortLabel()}. */
    public static Area uiArea(List<UiValidationFailure> fails) {
        List<String> lines = new ArrayList<>();
        fails.forEach(f -> lines.add(f.shortLabel()));
        return new Area(Kind.UI, "UI Validation", lines);
    }

    /** Area from a thrown error: an AssertJ/TestNG multi-entry message is split into its entries;
     *  anything else becomes one line (first line of the message, or the class name). */
    public static Area areaFrom(Kind kind, String title, Throwable t) {
        List<String> lines = new ArrayList<>();
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            lines.add(t.getClass().getSimpleName());
        } else {
            String[] chunks = msg.split("(?m)^\\s*\\d+\\)\\s*");
            if (chunks.length > 1) {
                for (int i = 1; i < chunks.length; i++) {
                    String first = firstLine(chunks[i]);
                    if (!first.isEmpty()) lines.add(first);
                }
            }
            if (lines.isEmpty()) lines.add(firstLine(msg));
        }
        return new Area(kind, title, lines);
    }

    private static String firstLine(String s) {
        String t = s == null ? "" : s.strip();
        int nl = t.indexOf('\n');
        String line = nl >= 0 ? t.substring(0, nl).stripTrailing() : t;
        return line.length() > 240 ? line.substring(0, 240) + "…" : line;
    }

    private static String buildMessage(List<Area> areas) {
        StringBuilder sb = new StringBuilder();
        sb.append(areas.size()).append(areas.size() == 1 ? " validation area failed:" : " validation areas failed:");
        for (Area a : areas) {
            sb.append('\n').append(a.title()).append(" — ").append(a.lines().size())
              .append(a.lines().size() == 1 ? " failure" : " failures").append(':');
            int shown = Math.min(MAX_LINES, a.lines().size());
            for (int i = 0; i < shown; i++) sb.append("\n  • ").append(a.lines().get(i));
            if (a.lines().size() > shown) {
                sb.append("\n  • …+").append(a.lines().size() - shown).append(" more");
            }
        }
        return sb.toString();
    }
}
