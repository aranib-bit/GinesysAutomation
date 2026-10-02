package com.gsl.utils;

/**
 * Immutable, caller-declared metadata for a page element — currently just a display/log name and
 * an explicit sensitive-data flag. Built via {@link #of(String)}/{@link #sensitive(String)} rather
 * than a public constructor, so a caller always states its sensitivity intent up front instead of
 * defaulting to non-sensitive by omission.
 *
 * <p>Consumed by {@link SensitiveDataMasker#isSensitive(ElementMeta, org.openqa.selenium.By)},
 * which treats an explicit {@link #isSensitive()} flag as authoritative but also independently
 * checks the name for a sensitive keyword — so a caller that forgets {@link #sensitive(String)}
 * is still caught if the name itself gives it away (e.g. {@code "password"}).
 */
public class ElementMeta {

    private final String name;
    private final boolean sensitive;

    private ElementMeta(String name, boolean sensitive) {
        this.name = name;
        this.sensitive = sensitive;
    }

    public String getName() {
        return name;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    /** Creates non-sensitive metadata for an element named {@code name}. */
    public static ElementMeta of(String name) {
        return new ElementMeta(name, false);
    }

    /** Creates metadata for an element named {@code name}, explicitly flagged as sensitive. */
    public static ElementMeta sensitive(String name) {
        return new ElementMeta(name, true);
    }
}
