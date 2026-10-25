package com.gsl.validation.ui;

import java.util.Locale;

/**
 * The kind of UI control an element is, as a business reader would name it. Detected from the DOM
 * (tag, input type, ExtJS CSS classes) by {@link #detect}, never via {@code Ext.ComponentQuery};
 * the EXPECTED type is supplied by the caller (ultimately from the Excel baseline).
 */
public enum UiElementType {
    TEXT_FIELD, NUMERIC_FIELD, DATE_FIELD, DROPDOWN, SEARCH_FIELD, SEARCH_ICON, TEXT_AREA, CHECKBOX,
    BUTTON, LABEL, DISPLAY_FIELD, FLOATING_GRID, UNKNOWN;

    /** Parses an Excel cell value ("Text Field", "TEXT_FIELD", "dropdown" ...); null/blank/"-" = null (not checked). */
    public static UiElementType parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        String key = s.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replace('/', '_');
        for (UiElementType t : values()) {
            if (t.name().equals(key)) return t;
        }
        throw new IllegalArgumentException("Unknown element type '" + raw + "' in baseline");
    }

    /** Parses a cell that may list alternatives ({@code "Text field|Dropdown"}); blank = empty list (not checked). */
    public static java.util.List<UiElementType> parseAll(String raw) {
        java.util.List<UiElementType> out = new java.util.ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split("[|]")) {
            UiElementType t = parse(part);
            if (t != null && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Human label for the report ("Text Field"). */
    public String label() {
        String[] w = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : w) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /**
     * @param tag      lower-case tag name of the element
     * @param inputType the {@code type} attribute ("" when none)
     * @param classes  space-separated CSS classes of the element AND its nearest {@code .x-field}/{@code .x-btn}/
     *                 {@code .x-grid} ancestor, concatenated
     */
    public static UiElementType detect(String tag, String inputType, String classes) {
        String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        String type = inputType == null ? "" : inputType.toLowerCase(Locale.ROOT);
        String c = " " + (classes == null ? "" : classes.toLowerCase(Locale.ROOT)) + " ";
        if (c.contains(" x-grid ") || c.contains(" x-grid-panel ") || c.contains(" x-boundlist ")) return FLOATING_GRID;
        if (type.equals("checkbox") || c.contains("x-form-type-checkbox") || c.contains(" x-form-cb ")) return CHECKBOX;
        if (t.equals("textarea") || c.contains("x-form-type-textarea") || c.contains(" x-form-textarea ")) return TEXT_AREA;
        if (c.contains("x-form-display-field") || c.contains(" x-form-type-displayfield ")) return DISPLAY_FIELD;
        if (c.contains("x-form-date-trigger") || c.contains("x-form-type-date")) return DATE_FIELD;
        if (c.contains("x-form-search-trigger") || c.contains("search-trigger")) return SEARCH_FIELD;
        if (c.contains("x-form-arrow-trigger") || c.contains("x-form-type-combo") || c.contains("x-form-type-combobox")
                || c.contains("x-form-trigger-wrap")) return DROPDOWN;
        if (c.contains("x-form-type-number") || type.equals("number")) return NUMERIC_FIELD;
        if (c.contains(" x-btn ") || t.equals("button") || type.equals("button") || type.equals("submit")) return BUTTON;
        if (c.contains("x-form-item-label") || t.equals("label")) return LABEL;
        if (c.contains("x-tool-search") || c.contains("mdi-magnify") || c.contains("search-icon")) return SEARCH_ICON;
        if (c.contains("x-form-type-text") || c.contains("x-form-text") || t.equals("input")) return TEXT_FIELD;
        return UNKNOWN;
    }
}
