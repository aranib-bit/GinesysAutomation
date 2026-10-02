package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.enums.LogCategory;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherActionsMenu;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Common Tier-1 list-page facade shared by AR/AP/GV voucher list pages — plan §17.
 *
 * <p>Mirrors, one level up, the pattern already proven at Tier 2
 * ({@link VoucherMainGridBase}/{@link VoucherListFooterBase}/{@link VoucherListGridHeaderBase} —
 * genuinely {@code extends}-inherited by {@code ARVoucherMainGrid}/{@code APVoucherMainGrid}/
 * {@code GeneralVoucherMainGrid} etc.). Until this class existed, the top-level facades
 * ({@code GeneralVoucherListPage}/{@code ArVoucherListPage}/{@code ApVoucherListPage}) had
 * <b>no common parent at all</b> — each was a hand-written standalone class implementing only
 * {@link VerifiablePage}, independently composing the same three Tier-2 components with
 * near-verbatim-identical method bodies. The clearest live symptom: the page-turning loop in
 * {@link VoucherGridPaginationFinder#selectDocumentAcrossPages} was already fully domain-agnostic
 * by signature, but only {@code GeneralVoucherListPage} had a facade method wired to call it —
 * AR/AP had no equivalent, purely because there was no common Tier-1 parent to place it on once.
 *
 * <p>The {@code TSelf} self-type parameter (CRTP) is what lets every fluent method keep returning
 * the exact concrete subclass type (e.g. {@code GeneralVoucherListPage}, not this base) without
 * each subclass overriding every method — preserving the "public API unchanged" property the
 * previous Tier-2 split already promised.
 *
 * @param <TGridHeader> the domain's {@link VoucherListGridHeaderBase} subclass
 * @param <TGrid>       the domain's {@link VoucherMainGridBase} subclass
 * @param <TFooter>     the domain's {@link VoucherListFooterBase} subclass
 * @param <TAddPage>    the domain's Add/Edit page type returned by {@link #clickAdd()}/
 *                      {@link #clickEditForVoucher()}/{@link #clickEditFromActions()}/
 *                      {@link #doubleClickToEdit(String)}
 * @param <TSelf>       the concrete facade subclass itself, for fluent-method covariant returns
 */
public abstract class VoucherListPageBase<
        TGridHeader extends VoucherListGridHeaderBase,
        TGrid extends VoucherMainGridBase,
        TFooter extends VoucherListFooterBase,
        TAddPage,
        TSelf extends VoucherListPageBase<TGridHeader, TGrid, TFooter, TAddPage, TSelf>>
        implements VerifiablePage {

    protected final TGridHeader gridHeader;
    protected final TGrid mainGrid;
    protected final TFooter gridFooter;

    protected VoucherListPageBase(TGridHeader gridHeader, TGrid mainGrid, TFooter gridFooter) {
        this.gridHeader = gridHeader;
        this.mainGrid = mainGrid;
        this.gridFooter = gridFooter;
    }

    public TGridHeader getGridHeader() { return gridHeader; }
    public TGrid getMainGrid() { return mainGrid; }
    public TFooter getGridFooter() { return gridFooter; }

    /**
     * Confirms the browser is actually showing this list page right now, via the page-header
     * text — a live signal independent of URL/menu-highlight state.
     */
    @Override
    public void verifyOnThisPage() {
        gridHeader.verifyOnThisPage();
    }

    @SuppressWarnings("unchecked")
    protected TSelf self() {
        return (TSelf) this;
    }

    /**
     * The one truly irreducible per-domain difference among {@link #clickAdd()}/
     * {@link #clickEditForVoucher()}/{@link #clickEditFromActions()}/
     * {@link #doubleClickToEdit(String)} — which concrete Add/Edit page to construct. Each
     * subclass implements this with one line (e.g. {@code return new GeneralVoucherAdd();}).
     */
    protected abstract TAddPage newAddPage();

    /**
     * If {@code page} can verify its own identity (i.e. implements {@link VerifiablePage}),
     * confirms the Add/Edit form actually rendered before handing it back to the caller — a click
     * that registers as successful can still leave the app silently stuck on the previous page
     * (confirmed live, FIN-GV-EDIT-DETAIL-01, run 20260908_135507: the Actions→Edit click
     * "succeeded" but the list page never actually navigated away, and the failure only surfaced
     * 30+ seconds later, deep inside the Edit form, as a confusing unrelated error). Mirrors the
     * exact same {@code instanceof VerifiablePage} + one-bounded-retry idiom
     * {@code OperationsSideMenuComponent.clickMenuItem} already uses for the side-menu click, one
     * layer up — reused here, not reinvented. A no-op (unchanged pre-existing behavior) for any
     * {@code TAddPage} that doesn't implement {@link VerifiablePage} (e.g. AR/AP's Add pages
     * today), so this carries zero regression risk for domains that haven't opted in yet.
     *
     * Once {@code verifyOnThisPage()} passes (first attempt or after reopen), logs a positive
     * {@code LogCategory.VERIFICATION} confirmation (e.g. "General Voucher Add page opened") —
     * previously this check was silent on success, so a report reader had no visible evidence it
     * ever ran. Each failure (bounded-wait timeout, and — if it comes to that — the post-reopen
     * re-check) is likewise logged under the same category via
     * {@link ExtentLogger#failCategorized}, which is never hidden by the business/debug toggle.
     *
     * <p>The first check uses {@link VerifiablePage#waitUntilVerified} (a short bounded retry)
     * rather than a single-shot {@code verifyOnThisPage()} call — confirmed live (run
     * {@code 20260909_094016}) that a single-shot check can race the view swap's header-text
     * write and false-negative even though the form is genuinely fine, which used to trigger this
     * exact {@code reopen.run()} needlessly — and the reopen click then genuinely failed, since
     * the form had by then actually opened and was covering the original button. The bounded
     * retry lets that race self-heal, so {@code reopen.run()} now only fires for a genuinely
     * stuck page (the scenario it was originally built for, live FIN-GV-EDIT-DETAIL-01, run
     * 20260908_135507).
     *
     * @param page        the just-constructed {@code TAddPage} to verify
     * @param actionLabel short label for the report message, e.g. {@code "Add"}/{@code "Edit"}
     * @param reopen      re-runs whatever click/select sequence is needed to try opening the form again
     * @throws RuntimeException the second verification's failure, uncaught, if the retry doesn't recover
     */
    private void verifyFormOpened(Object page, String actionLabel, Runnable reopen) {
        if (!(page instanceof VerifiablePage verifiable)) return;
        String label = gridHeader.getDomain().getVoucherLabel() + " " + actionLabel + " page";
        try {
            verifiable.waitUntilVerified(FORM_OPEN_VERIFY_TIMEOUT_SECONDS);
            ReportLogger.verification(label + " opened");
        } catch (RuntimeException firstAttemptFailure) {
            ExtentLogger.failCategorized(LogCategory.VERIFICATION, label + " did not open after the "
                    + "bounded wait (" + firstAttemptFailure.getMessage() + ") — attempting reopen");
            Log.warn("VoucherListPageBase: Add/Edit form didn't open after the first attempt ({}) "
                            + "— retrying once", firstAttemptFailure.getClass().getSimpleName());
            reopen.run();
            try {
                verifiable.waitUntilVerified(FORM_OPEN_VERIFY_TIMEOUT_SECONDS);
                ReportLogger.verification(label + " opened (after reopen)");
            } catch (RuntimeException secondAttemptFailure) {
                ExtentLogger.failCategorized(LogCategory.VERIFICATION,
                        label + " still not open after reopen — giving up: " + secondAttemptFailure.getMessage());
                throw secondAttemptFailure; // let a second failure propagate uncaught
            }
        }
    }

    /** Bounded-wait window for {@link #verifyFormOpened}'s page-identity checks — a real
     *  page-header write is near-instant once the view swap completes, so a few seconds is enough
     *  to bridge a render-completion race without masking a genuinely-stuck page for long. */
    private static final long FORM_OPEN_VERIFY_TIMEOUT_SECONDS = 5;

    @LogStep("Open New Voucher Form")
    public TAddPage clickAdd() {
        gridHeader.clickAddButton();
        TAddPage page = newAddPage();
        verifyFormOpened(page, "Add", gridHeader::clickAddButton);
        // Centralized here — every voucher Create test called this same shared method but only
        // one of them hand-wrote its own "Add form opened for X" summary line. The domain label
        // is already resolved one call away (verifyFormOpened uses the same accessor), so every
        // caller gets the enrichment for free instead of duplicating it per test class.
        // Item AG (Round 5): explicit "Success" wording, same as NavigationUtils' nav summary.
        com.gsl.reports.StepSummaryBuffer.record(
                "Add form opened for <b>" + gridHeader.getDomain().getVoucherLabel() + "</b> — <b>Success</b>");
        return page;
    }

    @LogStep("Open Voucher for Edit")
    public TAddPage clickEditForVoucher() {
        mainGrid.clickEditIcon();
        TAddPage page = newAddPage();
        verifyFormOpened(page, "Edit", mainGrid::clickEditIcon);
        return page;
    }

    /**
     * Opens the currently selected voucher for edit via the Actions→Edit path — clicks "Edit" in
     * the already-open Actions dropdown (see {@link #clickActions()}) and returns the opened
     * form.
     */
    @LogStep("Open Voucher for Edit via Actions")
    public TAddPage clickEditFromActions() {
        mainGrid.clickEdit();
        TAddPage page = newAddPage();
        verifyFormOpened(page, "Edit", mainGrid::clickEdit);
        return page;
    }

    /**
     * Document-number-aware overload (Round 10, RC-11) — on a confirmed "Actions→Edit disabled"
     * failure, re-selects {@code documentNumber} and re-opens Actions once before giving up,
     * instead of failing outright on an async grid reload that silently cleared selection between
     * the caller's own select step and this click (see
     * {@code VoucherMainGridBase.clickCheckbox}'s Javadoc for the confirmed root cause). Prefer
     * this overload at any Actions→Edit call site that already has the document number/strategy
     * in scope.
     */
    @LogStep("Open Voucher for Edit via Actions")
    public TAddPage clickEditFromActions(String documentNumber, DocumentSearchStrategy strategy) {
        Runnable reselect = () -> {
            selectDocumentForAction(documentNumber, strategy);
            clickActions();
        };
        reselect.run(); // initial select + open Actions — same as the previous inline call-site sequence
        mainGrid.clickEdit(reselect); // clicks Edit; on failure, reselect.run() then one retry
        TAddPage page = newAddPage();
        verifyFormOpened(page, "Edit", () -> { reselect.run(); mainGrid.clickEdit(reselect); });
        return page;
    }

    /**
     * Opens the given voucher for edit by double-clicking its row — the second live entry path
     * the app supports besides Actions→Edit. Page-turns to find it first (the double-click
     * analogue of {@link #selectDocumentInListPage(String)}/
     * {@link VoucherGridPaginationFinder#selectDocumentAcrossPages}) — a live run confirmed a
     * claimed voucher sitting past page 1 could never otherwise be found via this entry method,
     * since {@code EditGeneralVoucher} skips checkbox-selection (and therefore pagination)
     * entirely for {@code DOUBLE_CLICK_EDIT} on a not-Posted voucher.
     *
     * @param documentNumber the {@code scheme_docno} to open
     * @throws org.openqa.selenium.NoSuchElementException if the document isn't found after
     *         scanning every page
     */
    @LogStep("Open Voucher for Edit via Double-Click")
    public TAddPage doubleClickToEdit(String documentNumber) {
        VoucherGridPaginationFinder.doubleClickRowAcrossPages(documentNumber, mainGrid, gridFooter);
        TAddPage page = newAddPage();
        verifyFormOpened(page, "Edit", () ->
                VoucherGridPaginationFinder.doubleClickRowAcrossPages(documentNumber, mainGrid, gridFooter));
        return page;
    }

    /** Double-click analogue of {@link #selectDocumentFast(String, DocumentSearchStrategy)} for
     *  entry methods that open Edit via double-click rather than a checkbox — same cascade
     *  semantics (starting tier, fall-through on failure), using
     *  {@code doubleClickRowIfPresentOnCurrentPage} per tier and falling back to the proven
     *  {@link #doubleClickToEdit(String)} traversal. Default entry point — always tries the full
     *  cascade starting from {@link DocumentSearchStrategy#EXACT_MATCH}. */
    public TAddPage doubleClickToEditFast(String documentNumber) {
        return doubleClickToEditFast(documentNumber, DocumentSearchStrategy.EXACT_MATCH);
    }

    /** Starts the double-click cascade at {@code startingStrategy} instead of the top. */
    public TAddPage doubleClickToEditFast(String documentNumber, DocumentSearchStrategy startingStrategy) {
        if (startingStrategy.ordinal() <= DocumentSearchStrategy.EXACT_MATCH.ordinal()) {
            boolean foundExact = StepExecutor.call("Open via Exact-Match Filter (double-click)", () ->
                    GridColumnMenu.filterByExactMatch(CommonVoucherListColumn.VOUCHER_NO, documentNumber)
                            && mainGrid.doubleClickRowIfPresentOnCurrentPage(documentNumber));
            if (foundExact) {
                return newAddPage();
            }
            clearFilterAndRefreshList();
        }

        if (startingStrategy.ordinal() <= DocumentSearchStrategy.PARTIAL_MATCH.ordinal()) {
            boolean foundPartial = StepExecutor.call("Open via Partial-Match Filter (double-click)", () ->
                    GridColumnMenu.filterByContains(CommonVoucherListColumn.VOUCHER_NO, documentNumber)
                            && mainGrid.doubleClickRowIfPresentOnCurrentPage(documentNumber));
            if (foundPartial) {
                return newAddPage();
            }
            clearFilterAndRefreshList();
        }

        resetToFirstPageIfEnabled();
        return StepExecutor.call("Open via Page-by-Page Traversal (double-click)",
                () -> doubleClickToEdit(documentNumber));
    }

    @LogStep("Search Voucher by Document Number")
    public TSelf searchByVoucherNo(String voucherNo) {
        mainGrid.searchByVoucherNo(voucherNo);
        return self();
    }

    @LogStep("Click Delete for Voucher")
    public TSelf clickDeleteForVoucher() {
        mainGrid.clickDeleteForVoucher();
        return self();
    }

    /**
     * Document-number-aware overload (Round 10, RC-11) — see
     * {@link #clickEditFromActions(String, DocumentSearchStrategy)} for the rationale; same
     * re-select-and-retry-once recovery, for Actions→Delete. Delegates to
     * {@link #performGridAction(CommonVoucherActionsMenu, String, DocumentSearchStrategy)} — kept
     * as its own named method since {@code Delete} call sites read better than the generic name.
     */
    @LogStep("Click Delete for Voucher")
    public TSelf clickDeleteForVoucher(String documentNumber, DocumentSearchStrategy strategy) {
        return performGridAction(CommonVoucherActionsMenu.DELETE, documentNumber, strategy);
    }

    @LogStep("Unpost Voucher")
    public TSelf clickUnpost() {
        mainGrid.clickUnpost();
        return self();
    }

    @LogStep("Confirm Delete Dialog")
    public TSelf confirmDelete() {
        mainGrid.confirmDelete();
        return self();
    }

    /** Opens the list page's "Actions" dropdown. */
    public TSelf clickActions() {
        gridHeader.clickActions();
        return self();
    }

    /** Clicks the Refresh icon in the pagination footer. */
    public TSelf clickRefresh() {
        gridFooter.clickRefresh();
        return self();
    }

    /** Clicks "Clear Filter" in the header toolbar. */
    public TSelf clickClearFilter() {
        gridHeader.clickClearFilter();
        return self();
    }

    /**
     * Clears any leftover grid filter (e.g. a "Last N Days" date-range filter) and refreshes
     * the list, so a stale filter or paused-on-page state can't hide a target document from a
     * later paginated search ({@link #selectDocumentInListPage}). Domain-agnostic by
     * composition — {@link #gridHeader}/{@link #gridFooter} are already the concrete domain's
     * Tier-2 components (GV/AR/AP), so this one method works unchanged for every voucher list
     * page without needing to know which domain it's running against.
     */
    public TSelf clearFilterAndRefreshList() {
        clickClearFilter();
        clickRefresh();
        return self();
    }

    /** Clicks the Next Page control in the pagination footer. */
    public TSelf clickNextPage() {
        gridFooter.clickNextPage();
        return self();
    }

    /** Returns to the first page of the grid if the First Page control is currently enabled
     *  (no-op otherwise) — run before a paginated search (e.g. {@link #selectDocumentInListPage})
     *  so it isn't left scanning forward from a stale page position inherited from whatever ran
     *  before it.
     *
     *  <p>Checks {@link VoucherListFooterBase#getCurrentPageNo()} first — one cheap attribute
     *  read — before falling through to {@link VoucherListFooterBase#isFirstPageEnabled()}'s
     *  DOM probe. In the common case (grid already back on page 1 right after a filter clear/
     *  refresh, where the First-Page control is disabled/absent) that probe pays its full
     *  2-second timeout for a no-op; this fast path skips it entirely when the page number
     *  already reads 1. */
    public TSelf resetToFirstPageIfEnabled() {
        if (gridFooter.getCurrentPageNo() == 1) {
            return self();
        }
        if (gridFooter.isFirstPageEnabled()) {
            gridFooter.clickFirstPage();
        }
        return self();
    }

    /**
     * Selects the given document's row checkbox in the list grid, searching the currently loaded
     * page first and turning pages (via the footer's pagination controls) until found or the last
     * page is exhausted — the page-turning loop itself lives in
     * {@link VoucherGridPaginationFinder} (already domain-agnostic by signature; this facade
     * method is what was previously GV-only, closing the "Select Document" gap — plan §17).
     *
     * @param documentNumber the {@code scheme_docno} to select
     * @return {@code this} (concrete subclass type) for fluent chaining
     * @throws org.openqa.selenium.NoSuchElementException if the document isn't found after
     *         scanning every page
     */
    public TSelf selectDocumentInListPage(String documentNumber) {
        VoucherGridPaginationFinder.selectDocumentAcrossPages(documentNumber, mainGrid, gridFooter);
        return self();
    }

    /**
     * Tiered fast alternative to {@link #selectDocumentInListPage(String)} — tries an
     * exact-match filter on Voucher No. first, falls back to a partial-match filter, and only
     * falls all the way back to the proven page-by-page traversal if both filter tiers fail.
     * Default entry point — always tries the full cascade starting from
     * {@link DocumentSearchStrategy#EXACT_MATCH}.
     *
     * @param documentNumber the {@code scheme_docno} to select
     * @return {@code this} (concrete subclass type) for fluent chaining
     * @throws NoSuchElementException if the document isn't found by any tier
     */
    public TSelf selectDocumentFast(String documentNumber) {
        return selectDocumentFast(documentNumber, DocumentSearchStrategy.EXACT_MATCH);
    }

    /**
     * Starts the cascade at {@code startingStrategy} instead of the top — e.g. pass
     * {@code PARTIAL_MATCH} or {@code PAGE_BY_PAGE} directly when a caller already knows an
     * earlier tier won't help for this scenario (typically driven by a test-data-tagged
     * {@link DocumentSearchStrategy}, default {@code EXACT_MATCH} when untagged/null). Whichever
     * tier is started at, failure still falls through to the remaining, more-reliable tiers.
     */
    public TSelf selectDocumentFast(String documentNumber, DocumentSearchStrategy startingStrategy) {
        for (DocumentSearchStrategy strategy : DocumentSearchStrategy.values()) {
            if (strategy.ordinal() < startingStrategy.ordinal()) {
                continue;
            }
            if (strategy == DocumentSearchStrategy.PAGE_BY_PAGE) {
                resetToFirstPageIfEnabled();
            }
            if (strategy.attempt(this, documentNumber)) {
                return self();
            }
            if (strategy != DocumentSearchStrategy.PAGE_BY_PAGE) {
                clearFilterAndRefreshList();
            }
        }
        // Unreachable in practice — PAGE_BY_PAGE's attempt always either returns true or throws
        // NoSuchElementException itself — kept only so every path returns TSelf for the compiler.
        throw new NoSuchElementException(
                "selectDocumentFast: document [" + documentNumber + "] not found by any strategy.");
    }

    /**
     * The full "prepare list page + locate document" sequence every Revert/Delete/Edit test's own
     * "Select document" step needs: clear any stale filter, refresh, reset to page 1, then locate
     * and check the document's row via {@link #selectDocumentFast(String, DocumentSearchStrategy)}.
     * Consolidates what was previously 2-3 separately-chained calls, repeated — and, in several
     * call sites, incompletely copied (missing the clear-filter step entirely) — across every
     * Revert/Delete/Edit test class in GV/AR/AP. See {@link #doubleClickForAction(String,
     * DocumentSearchStrategy)} for the double-click-entry equivalent, and
     * {@link #performGridAction(CommonVoucherActionsMenu, String, DocumentSearchStrategy)} for the
     * one level up composite that also opens Actions and clicks a specific action item.
     */
    public TSelf selectDocumentForAction(String documentNumber, DocumentSearchStrategy strategy) {
        return clearFilterAndRefreshList().resetToFirstPageIfEnabled().selectDocumentFast(documentNumber, strategy);
    }

    /**
     * Double-click-entry analogue of {@link #selectDocumentForAction(String,
     * DocumentSearchStrategy)} — same clear-filter-first guarantee, for callers that open Edit via
     * {@link #doubleClickToEditFast(String, DocumentSearchStrategy)} instead of a checkbox+Actions
     * flow. Without this, {@code doubleClickToEditFast} only clears the filter *between* its own
     * failed tiers, never before the first one — a caller that doesn't independently clear the
     * filter first (as {@code EditGeneralVoucher} happens to, for an unrelated reason, before
     * either of its own branches) could have its EXACT_MATCH tier hidden behind a stale filter,
     * the same exposure {@link #selectDocumentForAction} closes for the checkbox path.
     */
    public TAddPage doubleClickForAction(String documentNumber, DocumentSearchStrategy strategy) {
        clearFilterAndRefreshList().resetToFirstPageIfEnabled();
        return doubleClickToEditFast(documentNumber, strategy);
    }

    /**
     * The common "select + open Actions + click a specific action item" composite — parametrized
     * by {@code menuItem} (DELETE/EDIT/REVERT/RELEASE) so this one method replaces what were
     * previously three separate, hand-copied near-duplicates ({@link #clickDeleteForVoucher(String,
     * DocumentSearchStrategy)}, {@link #clickEditFromActions(String, DocumentSearchStrategy)}, and
     * {@code GeneralVoucherListPage.clickRevert(String, DocumentSearchStrategy)}), each building
     * the identical "select, open Actions, click-with-retry" sequence for a different action.
     * Domain-specific dialog confirmation (e.g. {@code confirmDelete()}/{@code confirmRevert()})
     * is deliberately NOT included here — that part genuinely differs per domain and stays a
     * separate, explicit call by the caller.
     */
    public TSelf performGridAction(CommonVoucherActionsMenu menuItem, String documentNumber, DocumentSearchStrategy strategy) {
        Runnable reselect = () -> {
            selectDocumentForAction(documentNumber, strategy);
            clickActions();
        };
        reselect.run(); // initial select + open Actions
        mainGrid.clickGridAction(menuItem, reselect); // clicks the action item; on failure, reselect.run() then one retry
        return self();
    }

    // --------------------------------------------------------------------------------------
    // Column header menu (sort / filter / show-hide) — thin delegates to GridColumnMenu,
    // mirroring how selectDocumentInListPage above delegates to VoucherGridPaginationFinder.
    // --------------------------------------------------------------------------------------

    /** Sorts the grid by {@code column} ascending, replacing any existing sort. */
    public TSelf sortColumnAscending(IVoucherListColumn column) {
        GridColumnMenu.sortAscending(column);
        return self();
    }

    /** Sorts the grid by {@code column} descending, replacing any existing sort. */
    public TSelf sortColumnDescending(IVoucherListColumn column) {
        GridColumnMenu.sortDescending(column);
        return self();
    }

    /** Adds {@code column} as the next multi-sort priority (ascending), preserving any existing sort. */
    public TSelf addSortColumnAscending(IVoucherListColumn column) {
        GridColumnMenu.addSortAscending(column);
        return self();
    }

    /** Adds {@code column} as the next multi-sort priority (descending), preserving any existing sort. */
    public TSelf addSortColumnDescending(IVoucherListColumn column) {
        GridColumnMenu.addSortDescending(column);
        return self();
    }

    /** Applies a "Contains anywhere..." filter to {@code column} (STRING columns only, this pass). */
    public TSelf filterColumnByContains(IVoucherListColumn column, String value) {
        GridColumnMenu.filterByContains(column, value);
        return self();
    }

    /** Applies an "Exact match..." filter to {@code column} (STRING columns only, this pass). */
    public TSelf filterColumnByExactMatch(IVoucherListColumn column, String value) {
        GridColumnMenu.filterByExactMatch(column, value);
        return self();
    }

    /** Shows {@code column} via the "Columns ▸" chooser (no-op if already visible). */
    public TSelf showColumn(IVoucherListColumn column) {
        GridColumnMenu.showColumn(column);
        return self();
    }

    /** Hides {@code column} via the "Columns ▸" chooser (no-op if already hidden).
     *  @throws com.gsl.exceptions.MandatoryColumnException if {@code column.mandatory()} */
    public TSelf hideColumn(IVoucherListColumn column) {
        GridColumnMenu.hideColumn(column);
        return self();
    }

    /** Whether {@code column} is currently visible in the live grid. */
    public boolean isColumnVisible(IVoucherListColumn column) {
        return GridColumnMenu.isColumnCurrentlyVisible(column);
    }

    /** The multi-sort priority badge (e.g. {@code (1)}, {@code (2)}) currently shown on
     *  {@code column}'s header, if any. */
    public Optional<Integer> getColumnSortOrder(IVoucherListColumn column) {
        return GridColumnMenu.getMultiSortOrder(column);
    }

    /** The 0-based left-to-right position of {@code column} among currently-visible columns —
     *  empty if not currently visible. Position is mutable (drag-reorder) independent of
     *  hideability, so this always reflects the live DOM, never an assumed fixed layout. */
    public java.util.OptionalInt getColumnPosition(IVoucherListColumn column) {
        return GridColumnMenu.getColumnPosition(column);
    }

    /** Drags {@code column}'s header to {@code targetPosition} (0-based, among currently-visible
     *  columns) — the same interaction a user performs to reorder grid columns. */
    public TSelf reorderColumn(IVoucherListColumn column, int targetPosition) {
        GridColumnMenu.reorderColumn(column, targetPosition);
        return self();
    }
}
