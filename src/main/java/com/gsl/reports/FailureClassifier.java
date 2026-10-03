package com.gsl.reports;

import com.gsl.validation.CombinedValidationError;
import com.gsl.validation.ui.UiValidationFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a test's throwable (plus the structured UI mismatches captured for it) into the three
 * things the coverage workbook needs: a {@link FailureCategory}, a one-cell readable summary, and
 * one {@link FailureDetail} per mismatch. Pure - no Selenium/Extent/DB access - so it is unit
 * tested directly.
 *
 * <p>Why not just {@code getMessage()}: a {@link CombinedValidationError}'s message begins with the
 * useless line "1 validation area failed:" and the area contents are on later lines, so the first
 * line alone told the reader nothing. The summary here is rebuilt from the error's areas instead.
 */
public final class FailureClassifier {

    private FailureClassifier() {}

    /** Result of {@link #classify}. */
    public record Outcome(FailureCategory category, String summary, String fullMessage, List<FailureDetail> details) {}

    private static final int SUMMARY_MAX = 450;
    private static final int FULL_MAX = 4000;
    private static final int MAX_DETAILS = 60;

    private static final Pattern UI_LINE = Pattern.compile("^(.*?)\\s*expected '(.*?)', actual '(.*)'$", Pattern.DOTALL);
    private static final Pattern GENERIC_EXP_ACT = Pattern.compile(
            "(?is)expected:?\\s*[<\\[']?(.*?)[>\\]']?\\s*(?:,|but)?\\s*(?:but )?(?:was|actual)[:\\s]*[<\\['](.*?)[>\\]']?\\s*$");

    /**
     * @param t        the test's failure/skip throwable, may be {@code null}
     * @param status   {@code PASS}, {@code FAIL} or {@code SKIP}
     * @param uiFails  UI-contract FAILs captured for the test (may be empty); used for structured UI rows
     */
    public static Outcome classify(Throwable t, String status, List<UiValidationFailure> uiFails) {
        if ("PASS".equals(status)) {
            return new Outcome(FailureCategory.NONE, "", "", List.of());
        }
        if (t == null) {
            return new Outcome("SKIP".equals(status) ? FailureCategory.DATA_SHORTAGE : FailureCategory.OTHER,
                    "", "", List.of());
        }
        CombinedValidationError combined = findCombined(t);
        if (combined != null) {
            return fromCombined(combined, uiFails == null ? List.of() : uiFails);
        }
        String message = unescapeHtml(t.getMessage() == null ? "" : t.getMessage()).strip();
        String first = firstNonEmptyLine(message);
        if (first.isEmpty()) first = t.getClass().getSimpleName();
        FailureCategory cat = categoryOf(t, message);
        List<FailureDetail> details = new ArrayList<>();
        if (!"SKIP".equals(status)) {
            // AssertJ-style multi-entry messages split into one row per entry.
            CombinedValidationError.Area area = CombinedValidationError.areaFrom(CombinedValidationError.Kind.OTHER,
                    "Test flow", t);
            for (String line : area.lines()) {
                if (details.size() >= MAX_DETAILS) break;
                details.add(parsed("Test flow", unescapeHtml(line)));
            }
        }
        return new Outcome(cat, cap(first, SUMMARY_MAX), cap(withType(t, message), FULL_MAX), details);
    }

    // -- combined validation errors ---------------------------------------------------------------

    private static Outcome fromCombined(CombinedValidationError e, List<UiValidationFailure> uiFails) {
        List<FailureDetail> details = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        boolean hasDb = false, hasSnap = false, hasUi = false;
        FailureCategory otherCat = null;
        for (CombinedValidationError.Area a : e.areas()) {
            if (summary.length() > 0) summary.append(" | ");
            List<String> lines = new ArrayList<>();
            for (String l : a.lines()) lines.add(unescapeHtml(l));
            summary.append(a.title()).append(" (").append(lines.size()).append("): ")
                    .append(String.join("; ", lines.subList(0, Math.min(3, lines.size()))));
            if (lines.size() > 3) summary.append("; +").append(lines.size() - 3).append(" more");

            String areaName = switch (a.kind()) {
                case UI -> "UI";
                case DATABASE -> "Database";
                case SNAPSHOT -> "Snapshot";
                case OTHER -> "Test flow";
            };
            switch (a.kind()) {
                case UI -> hasUi = true;
                case DATABASE -> hasDb = true;
                case SNAPSHOT -> hasSnap = true;
                case OTHER -> {
                    if (otherCat == null) otherCat = categoryOfText(String.join(" ", lines));
                }
            }
            if (a.kind() == CombinedValidationError.Kind.UI && !uiFails.isEmpty()) {
                continue;   // structured rows added below, once
            }
            for (String line : lines) {
                if (details.size() >= MAX_DETAILS) break;
                details.add(parsed(areaName, line));
            }
        }
        if (hasUi && !uiFails.isEmpty()) {
            List<FailureDetail> ui = new ArrayList<>();
            for (UiValidationFailure f : uiFails) {
                if (!f.isFail()) continue;
                ui.add(FailureDetail.builder().area("UI")
                        .location(unescapeHtml(String.join(" > ", f.path())))
                        .component(nz(f.component())).property(nz(f.property()))
                        .expected(unescapeHtml(nz(f.expected()))).actual(unescapeHtml(nz(f.actual())))
                        .detail(unescapeHtml(f.shortLabel())).build());
            }
            details.addAll(0, ui.subList(0, Math.min(ui.size(), MAX_DETAILS)));
            if (details.size() > MAX_DETAILS) details = new ArrayList<>(details.subList(0, MAX_DETAILS));
        }
        FailureCategory cat = hasDb ? FailureCategory.DB_VALIDATION
                : hasSnap ? FailureCategory.SNAPSHOT_VALIDATION
                : hasUi ? FailureCategory.UI_VALIDATION
                : (otherCat != null ? otherCat : FailureCategory.OTHER);
        return new Outcome(cat, cap(summary.toString(), SUMMARY_MAX),
                cap(unescapeHtml(e.getMessage() == null ? "" : e.getMessage()), FULL_MAX), details);
    }

    private static CombinedValidationError findCombined(Throwable t) {
        int guard = 0;
        for (Throwable c = t; c != null && guard++ < 12; c = c.getCause()) {
            if (c instanceof CombinedValidationError e) return e;
        }
        return null;
    }

    // -- categorisation ------------------------------------------------------------------------------

    static FailureCategory categoryOf(Throwable t, String message) {
        int guard = 0;
        for (Throwable c = t; c != null && guard++ < 12; c = c.getCause()) {
            String n = c.getClass().getName();
            if (n.contains("SkipException")) {
                String m = String.valueOf(c.getMessage()).toLowerCase();
                return m.contains("circuit breaker") ? FailureCategory.CIRCUIT_BREAKER : FailureCategory.DATA_SHORTAGE;
            }
            if (n.contains("TimeoutException") || n.contains("LoaderStillInProgress")) return FailureCategory.TIMEOUT;
            if (n.contains("ElementClickInterceptedException") || n.contains("ElementNotInteractableException")) {
                return FailureCategory.CLICK_BLOCKED;
            }
            if (n.contains("NoSuchElementException") || n.contains("StaleElementReferenceException")) {
                return FailureCategory.ELEMENT_NOT_FOUND;
            }
            if (n.contains("SQLException") || n.contains("JdbiException") || n.contains("DataAccess")) {
                return FailureCategory.DB_ERROR;
            }
        }
        return categoryOfText(message == null ? "" : message);
    }

    static FailureCategory categoryOfText(String text) {
        String m = text.toLowerCase();
        if (m.contains("timed out") || m.contains("timeout")) return FailureCategory.TIMEOUT;
        if (m.contains("click intercepted") || m.contains("not clickable")) return FailureCategory.CLICK_BLOCKED;
        if (m.contains("no such element") || m.contains("unable to locate")) return FailureCategory.ELEMENT_NOT_FOUND;
        if (m.contains("rejected") || m.contains("validation error") || m.contains("not allowed")
                || m.contains("error message") || m.contains("was blocked")) {
            return FailureCategory.APP_REJECTION;
        }
        if (m.contains("expected") || m.contains("assert")) return FailureCategory.ASSERTION;
        return FailureCategory.OTHER;
    }

    // -- helpers -----------------------------------------------------------------------------------

    private static FailureDetail parsed(String area, String line) {
        Matcher ui = UI_LINE.matcher(line);
        if (ui.matches()) {
            String lhs = ui.group(1);
            String loc = "", comp = lhs, prop = "";
            int colon = lhs.lastIndexOf(": ");
            if (colon > 0) {
                prop = lhs.substring(colon + 2).strip();
                comp = lhs.substring(0, colon).strip();
            }
            int gt = comp.lastIndexOf(" > ");
            if (gt > 0) {
                loc = comp.substring(0, gt).strip();
                comp = comp.substring(gt + 3).strip();
            }
            return FailureDetail.builder().area(area).location(loc).component(comp).property(prop)
                    .expected(ui.group(2)).actual(ui.group(3)).detail(line).build();
        }
        Matcher g = GENERIC_EXP_ACT.matcher(line);
        if (g.find()) {
            return FailureDetail.builder().area(area).location("").component("").property("")
                    .expected(g.group(1).strip()).actual(g.group(2).strip()).detail(line).build();
        }
        return FailureDetail.builder().area(area).location("").component("").property("")
                .expected("").actual("").detail(line).build();
    }

    private static String withType(Throwable t, String message) {
        String type = t.getClass().getSimpleName();
        return message.isEmpty() ? type : type + ": " + message;
    }

    private static String firstNonEmptyLine(String s) {
        return s.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
    }

    private static String cap(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * Decodes the HTML entities that leak into failure messages (the Extent layer HTML-escapes
     * text, so {@code <}/{@code >}/quotes/apostrophes/ampersands arrive as {@code &lt;} etc.):
     * the common named entities plus decimal and hex numeric references. Unknown entities are left as-is.
     */
    public static String unescapeHtml(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = decode(ent);
                    if (rep != null) {
                        out.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String decode(String ent) {
        switch (ent) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "ndash": return "-";
            case "mdash": return "-";
            case "rarr": return "->";
            case "hellip": return "...";
            default: break;
        }
        try {
            if (ent.startsWith("#x") || ent.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
            }
            if (ent.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(ent.substring(1))));
            }
        } catch (RuntimeException ignored) {
            // fall through: leave the text untouched
        }
        return null;
    }
}
