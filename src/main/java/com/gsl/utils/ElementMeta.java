package com.gsl.utils;

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

    public static ElementMeta of(String name) {
        return new ElementMeta(name, false);
    }

    public static ElementMeta sensitive(String name) {
        return new ElementMeta(name, true);
    }
}
