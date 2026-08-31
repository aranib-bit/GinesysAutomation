package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.GridColumnMenu;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListPageBase;
import com.gsl.reports.StepExecutor;

import java.util.function.BiPredicate;

/**
 * The document-lookup mechanism used to select/open an existing voucher in the list grid —
 * mirrors {@link VoucherEditEntryMethod}'s shape and placement: a domain-agnostic fixture-layer
 * enum a test case can be tagged with (default {@link #EXACT_MATCH} when the test data's own
 * search-strategy field is {@code null}), reusable across GV/AR/AP alike, same as
 * {@code VoucherEditEntryMethod} is meant to be.
 *
 * <p><b>Unlike {@code VoucherEditEntryMethod}'s plain two-way branch</b> (no fallback between
 * {@code ACTIONS_EDIT}/{@code DOUBLE_CLICK_EDIT} — if one fails, the test just fails), this one
 * is a CASCADE: the tagged value is only the STARTING tier —
 * {@code VoucherListPageBase#selectDocumentFast}/{@code #doubleClickToEditFast} still fall
 * through to the remaining, more-reliable tiers on failure (explicit user confirmation: "Exact
 * Match if fails then move to partial match if that fails then move to page by page").
 *
 * <p><b>One deliberate layering exception, flagged rather than hidden:</b>
 * {@code VoucherEditEntryMethod} is a pure, page-unaware data tag — the test class does its own
 * two-way branch directly, so the pages layer never imports the fixture layer. This enum's
 * cascade is inherently more than a two-way branch (3 tiers, shared fallback/reset logic,
 * needs {@code mainGrid}/{@code newAddPage()} access {@code VoucherListPageBase} only exposes as
 * {@code protected}/instance-scoped) — centralizing the cascade as an external fixture-layer
 * static helper was considered and rejected (it would need {@code newAddPage()}, which is
 * {@code protected} and inaccessible from another package on an arbitrary instance). So
 * {@code VoucherListPageBase.selectDocumentFast}/{@code .doubleClickToEditFast} stay instance
 * methods on the pages-layer class, and that class takes this enum as a plain parameter type —
 * the one deliberate, narrow exception to "fixture depends on pages, not the reverse" in this
 * codebase. Defensible because the enum itself carries zero fixture-only dependencies (no DTOs,
 * no scenario types, nothing that would drag fixture-layer concepts into {@code com.gsl.pages.*})
 * — structurally it could have lived in pages unchanged; it's only placed here so it can double
 * as a test-data tag, exactly as requested.
 */
public enum DocumentSearchStrategy {

    /** Filters the grid to an exact match on Voucher No. — fastest tier. */
    EXACT_MATCH((page, documentNumber) -> StepExecutor.call("Select via Exact-Match Filter", () ->
            GridColumnMenu.filterByExactMatch(CommonVoucherListColumn.VOUCHER_NO, documentNumber)
                    && page.getMainGrid().selectDocumentIfPresentOnCurrentPage(documentNumber))),

    /** Filters the grid via "Contains anywhere..." on Voucher No. — broader net than exact-match,
     *  still filter-based and fast. */
    PARTIAL_MATCH((page, documentNumber) -> StepExecutor.call("Select via Partial-Match Filter", () ->
            GridColumnMenu.filterByContains(CommonVoucherListColumn.VOUCHER_NO, documentNumber)
                    && page.getMainGrid().selectDocumentIfPresentOnCurrentPage(documentNumber))),

    /** Full page-by-page traversal — the existing, proven fallback if both filter tiers fail. */
    PAGE_BY_PAGE((page, documentNumber) -> StepExecutor.call("Select via Page-by-Page Traversal", () -> {
        page.selectDocumentInListPage(documentNumber);  // throws NoSuchElementException if truly absent
        return true;
    }));

    // Raw/wildcarded VoucherListPageBase — enums can't be generic, and the predicate body only
    // ever needs page.getMainGrid()/page.selectDocumentInListPage(...), neither of which requires
    // the exact generic parameters, so this loses nothing in practice.
    private final BiPredicate<VoucherListPageBase<?, ?, ?, ?, ?>, String> attempt;

    DocumentSearchStrategy(BiPredicate<VoucherListPageBase<?, ?, ?, ?, ?>, String> attempt) {
        this.attempt = attempt;
    }

    /** Runs this tier's attempt (each already wrapped in its own reported {@code StepExecutor}
     *  step) and returns whether it found+selected the document. */
    public boolean attempt(VoucherListPageBase<?, ?, ?, ?, ?> page, String documentNumber) {
        return attempt.test(page, documentNumber);
    }
}
