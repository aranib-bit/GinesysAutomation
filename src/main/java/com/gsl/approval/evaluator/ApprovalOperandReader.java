package com.gsl.approval.evaluator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.approval.evaluator.ApprovalEvaluationException.Kind;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalDataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the raw values of a document fact and of a rule condition into the normalized form {@link ApprovalOperator} compares: money and
 * numbers as {@code BigDecimal} (never double), dates as {@code LocalDate}, text and lookup codes as trimmed lower-case strings. Lookup
 * variables compare by code ({@code valueCode}), everything else by {@code valueText}.
 * TODO-confirm against the application: text matching is read as case-insensitive.
 */
final class ApprovalOperandReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ApprovalOperandReader() {}

    /** A fact value in normalized form; {@code null} stays {@code null}. */
    static Object actual(ApprovalDataType type, Object raw, String varCode) {
        return scalar(type, raw, "fact " + varCode);
    }

    /** The condition's expected value in normalized form: a scalar, or a list for IN / BETWEEN, or null for NULL / NOTNULL. */
    static Object expected(ApprovalCondition condition, ApprovalDataType type, ApprovalOperator operator) {
        if (!operator.takesValue()) {
            return null;
        }
        String where = "rule condition " + condition.getVarCode() + " " + operator;
        String source = type == ApprovalDataType.LOOKUP ? firstNonBlank(condition.getValueCode(), condition.getValueText())
                : firstNonBlank(condition.getValueText(), condition.getValueCode());
        if (source == null) {
            throw new ApprovalEvaluationException(Kind.BAD_VALUE, where + " has no value");
        }
        if (operator.takesList()) {
            List<Object> values = new ArrayList<>();
            for (String part : splitList(source, where)) {
                values.add(scalar(type, part, where));
            }
            if (operator == ApprovalOperator.BETWEEN && values.size() != 2) {
                throw new ApprovalEvaluationException(Kind.BAD_VALUE, where + " needs exactly two bounds but has '" + source + "'");
            }
            return values;
        }
        return scalar(type, source, where);
    }

    private static Object scalar(ApprovalDataType type, Object raw, String where) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (type) {
                case NUMBER -> raw instanceof BigDecimal b ? b : new BigDecimal(raw.toString().trim());
                case DATE -> toDate(raw);
                case TEXT, LOOKUP -> raw.toString().trim().toLowerCase(Locale.ROOT);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new ApprovalEvaluationException(Kind.BAD_VALUE, "Cannot read '" + raw + "' as " + type + " for " + where, e);
        }
    }

    private static LocalDate toDate(Object raw) {
        if (raw instanceof LocalDate d) {
            return d;
        }
        if (raw instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        String text = raw.toString().trim();
        return text.length() > 10 ? LocalDateTime.parse(text).toLocalDate() : LocalDate.parse(text);
    }

    /** A JSON array ({@code [193,206]}, {@code ["a","b"]}) or a comma-separated list. */
    private static List<String> splitList(String source, String where) {
        String text = source.trim();
        if (text.startsWith("[")) {
            try {
                List<String> out = new ArrayList<>();
                JSON.readTree(text).forEach(n -> out.add(n.asText()));
                return out;
            } catch (JsonProcessingException e) {
                throw new ApprovalEvaluationException(Kind.BAD_VALUE, where + " has an unreadable list '" + source + "'", e);
            }
        }
        List<String> out = new ArrayList<>();
        for (String part : text.split(",")) {
            out.add(part.trim());
        }
        return out;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
