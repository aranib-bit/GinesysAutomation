package com.gsl.validation.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Thread-local stack of report-node names. Every {@link UiValidationFailure} created while a
 * {@link #node} is active is tagged with the current stack as its {@code path}, so
 * {@link UiValidationReporter} can render a nested tree (e.g. {@code Expense/Income Detail > Row 2
 * > Allocate Sites > After fill}) without any existing validator needing a new parameter.
 */
public final class UiValidationScope {

    private UiValidationScope() {}

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** Runs {@code body} with {@code name} pushed on the path stack and returns its results. */
    public static List<UiValidationFailure> node(String name, Supplier<List<UiValidationFailure>> body) {
        Deque<String> stack = STACK.get();
        stack.addLast(name);
        try {
            return body.get();
        } finally {
            stack.removeLast();
        }
    }

    /** Snapshot of the current path, outermost node first; empty when no node is active. */
    public static List<String> currentPath() {
        return new ArrayList<>(STACK.get());
    }
}
