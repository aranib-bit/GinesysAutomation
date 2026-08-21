package com.gsl.database;

/**
 * Plan §15a — implemented by a test-data DTO whose {@code load()}/data-source resolution claims
 * one specific document via {@link DocumentReservationRegistry#tryReserve(String)} <em>ahead of
 * time</em> (before the {@code @Test} method that consumes it even starts) rather than claiming
 * and releasing it within a single row's own execution.
 *
 * <h3>Why this exists</h3>
 * {@code WebTest.tearDown()}'s {@code @AfterMethod} used to call
 * {@link DocumentReservationRegistry#releaseCurrentThread()} unconditionally after every row —
 * fine for a single-row-per-{@code load()} data source (AR Edit, AP Create/Edit), but wrong for a
 * data source that claims ALL of a method's rows upfront in one {@code load()} pass (confirmed:
 * {@code DbGeneralVoucherEditDataSource}, {@code DbGeneralVoucherRevertDataSource}) — releasing
 * "everything this thread holds" after just the FIRST row prematurely reopens the still-pending
 * rows to a sibling parallel thread while this thread still intends to use them.
 *
 * <p>A DTO implementing this interface lets {@code tearDown} release <em>only the one document
 * this specific row used</em> — the exact string already passed to {@code tryReserve} — instead
 * of the whole thread's reservation set. A DTO that does NOT implement this interface (every other
 * domain/data source today) is unaffected: {@code tearDown} falls straight through to its original
 * {@code releaseCurrentThread()} behavior, unchanged.
 */
public interface ReservedDocumentKeyAware {

    /**
     * @return the exact key this row's document was reserved under (i.e. the same string passed
     *         to {@link DocumentReservationRegistry#tryReserve(String)} when it was claimed), or
     *         {@code null} if this particular instance was never claimed through the registry.
     */
    String getReservedDocumentKey();
}
