package com.gsl.validation.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Accumulates API checks (never throws) as pass/fail text lines and as structured {@link ApiCheckRow}s, so one run reports every discrepancy. */
final class ApiChecks {
    final List<String> passed = new ArrayList<>();
    final List<String> failed = new ArrayList<>();
    final List<ApiCheckRow> rows = new ArrayList<>();
    /** group key ("Row 1") -> report title with the Sub Ledger / Site / Payment tags; absent = plain group name. */
    final java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();

    void title(String group, String title) {
        titles.put(group, title);
    }

    void eq(String what, Object expected, Object actual) {
        boolean ok = Objects.equals(expected, actual);
        if (ok) passed.add(what + " = " + show(actual));
        else failed.add(what + ": expected " + show(expected) + " but was " + show(actual));
        rows.add(ApiCheckRow.of(what, plain(expected), plain(actual), ok));
    }

    void money(String what, BigDecimal expected, BigDecimal actual) {
        boolean ok = expected != null && actual != null && expected.compareTo(actual) == 0;
        if (ok) passed.add(what + " = " + actual.stripTrailingZeros().toPlainString());
        else failed.add(what + ": expected " + show(expected) + " but was " + show(actual));
        rows.add(ApiCheckRow.of(what, plain(expected), plain(actual), ok));
    }

    /** A failure that has no expected/actual pair (e.g. the voucher could not be read back). */
    void fail(String message) {
        failed.add(message);
        rows.add(ApiCheckRow.of("Read-back", "available", message, false));
    }

    private static String show(Object o) {
        return o == null ? "(none)" : o instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : "'" + o + "'";
    }

    private static String plain(Object o) {
        return o == null ? "(none)" : o instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : String.valueOf(o);
    }
}
