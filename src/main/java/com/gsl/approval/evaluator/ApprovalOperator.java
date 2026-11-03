package com.gsl.approval.evaluator;

import com.gsl.approval.model.ApprovalDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.gsl.approval.model.ApprovalDataType.DATE;
import static com.gsl.approval.model.ApprovalDataType.LOOKUP;
import static com.gsl.approval.model.ApprovalDataType.NUMBER;
import static com.gsl.approval.model.ApprovalDataType.TEXT;

/**
 * The comparison operators an approval rule condition can use ({@code ApprovalRule/GetVariables} lists them per variable). Each constant is a
 * {@link BiPredicate} over an already-normalized actual value and expected value (see {@link ApprovalOperandReader}): {@code BigDecimal} for
 * NUMBER, {@code LocalDate} for DATE, lower-cased {@code String} for TEXT and LOOKUP. {@code IN} expects a {@code List}, {@code BETWEEN} a
 * two-element {@code List} (low, high; inclusive). A {@code null} actual value is only ever true for {@link #NULL}.
 */
public enum ApprovalOperator {

    EQ(EnumSet.of(TEXT, LOOKUP, NUMBER, DATE), (a, e) -> a != null && compare(a, e) == 0),
    IN(EnumSet.of(TEXT, LOOKUP), (a, e) -> a != null && ((List<?>) e).stream().anyMatch(x -> compare(a, x) == 0)),
    GT(EnumSet.of(NUMBER, DATE), (a, e) -> a != null && compare(a, e) > 0),
    GTE(EnumSet.of(NUMBER, DATE), (a, e) -> a != null && compare(a, e) >= 0),
    LT(EnumSet.of(NUMBER, DATE), (a, e) -> a != null && compare(a, e) < 0),
    LTE(EnumSet.of(NUMBER, DATE), (a, e) -> a != null && compare(a, e) <= 0),
    BETWEEN(EnumSet.of(NUMBER, DATE), (a, e) -> {
        List<?> bounds = (List<?>) e;
        return a != null && compare(a, bounds.get(0)) >= 0 && compare(a, bounds.get(1)) <= 0;
    }),
    /** Text contains the expected text. TODO-confirm against the application: "like" is read as contains, not as a SQL pattern. */
    LIKE(EnumSet.of(TEXT), (a, e) -> a != null && a.toString().contains(e.toString())),
    NULL(EnumSet.of(TEXT, DATE), (a, e) -> a == null || a.toString().isBlank()),
    NOTNULL(EnumSet.of(TEXT, DATE), (a, e) -> a != null && !a.toString().isBlank());

    private final Set<ApprovalDataType> supportedTypes;
    private final BiPredicate<Object, Object> predicate;

    ApprovalOperator(Set<ApprovalDataType> supportedTypes, BiPredicate<Object, Object> predicate) {
        this.supportedTypes = supportedTypes;
        this.predicate = predicate;
    }

    /** {@code actual OP expected} for values normalized as described on the class. */
    public boolean test(Object actual, Object expected) {
        return predicate.test(actual, expected);
    }

    public boolean supports(ApprovalDataType type) {
        return supportedTypes.contains(type);
    }

    /** NULL and NOTNULL take no expected value. */
    public boolean takesValue() {
        return this != NULL && this != NOTNULL;
    }

    public boolean takesList() {
        return this == IN || this == BETWEEN;
    }

    public static ApprovalOperator fromWire(String text) {
        if (text == null) {
            throw new ApprovalEvaluationException(ApprovalEvaluationException.Kind.UNSUPPORTED_OPERATOR, "Rule condition has no operator");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApprovalEvaluationException(ApprovalEvaluationException.Kind.UNSUPPORTED_OPERATOR, "Unsupported rule operator '" + text + "'", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }
}
