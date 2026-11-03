package com.gsl.approval.service;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Repeats a check until it yields a value or the poll window ends. A bound on a wait that normally ends at once (the next task appearing, the
 * document posting), never a fixed delay and never a place to hide a slow application: the window comes from configuration.
 */
public interface ApprovalPoller {

    /** The first present result of {@code check}, or empty once the window is over. The check always runs at least once. */
    <T> Optional<T> until(Supplier<Optional<T>> check);
}
