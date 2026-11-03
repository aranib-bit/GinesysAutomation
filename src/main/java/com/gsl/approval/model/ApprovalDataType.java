package com.gsl.approval.model;

import java.util.Locale;

/** The data type of a rule variable as {@code ApprovalRule/GetVariables} reports it; decides how values are read and which operators apply. */
public enum ApprovalDataType {
    TEXT, LOOKUP, NUMBER, DATE;

    public static ApprovalDataType fromWire(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Rule variable data type is missing");
        }
        return valueOf(text.trim().toUpperCase(Locale.ROOT));
    }
}
