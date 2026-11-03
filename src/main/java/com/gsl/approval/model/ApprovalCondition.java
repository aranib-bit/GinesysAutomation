package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

/** One rule condition: {@code variable operator value}. A rule's conditions are ANDed; the application allows no OR and no nesting. */
@Value
@Builder
public class ApprovalCondition {
    /** {@code H} header or {@code L} line (a line condition holds when any line matches). */
    String basis;
    String varCode;
    String varName;
    String dataType;
    String operator;
    String valueText;
    /** A code for LOOKUP variables, or a JSON array of codes for {@code IN}. */
    String valueCode;

    public boolean isLineBasis() {
        return "L".equalsIgnoreCase(basis);
    }
}
