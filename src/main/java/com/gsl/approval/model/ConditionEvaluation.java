package com.gsl.approval.model;

import lombok.Value;

/** How one rule condition evaluated against the document, for the report. Values are shown as the application's text, never a token or secret. */
@Value
public class ConditionEvaluation {
    String varCode;
    String operator;
    String expected;
    String actual;
    boolean matched;
}
