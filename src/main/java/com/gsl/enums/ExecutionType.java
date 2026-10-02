package com.gsl.enums;

/**
 * Named execution/user-set identifier, mirroring the two-way split used elsewhere in the
 * framework for partitioning test users into pools (see {@code com.gsl.testdata.ExecutionSet}).
 * TODO-confirm: no call site in this codebase currently references this specific enum type — the
 * live "execution.type" config property ({@code ExecutionConfig#executionType()}) is read as a
 * plain {@code String}, not this enum — so its intended wiring point is unclear; do not assume it
 * is dead code without checking for newer callers first.
 */
public enum ExecutionType {
    SET1, SET2
}
