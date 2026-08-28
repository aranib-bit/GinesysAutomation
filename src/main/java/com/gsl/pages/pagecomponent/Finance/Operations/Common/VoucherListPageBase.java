package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;

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

    @LogStep("Open New Voucher Form")
    public TAddPage clickAdd() {
        gridHeader.clickAddButton();
        return newAddPage();
    }

    @LogStep("Open Voucher for Edit")
    public TAddPage clickEditForVoucher() {
        mainGrid.clickEditIcon();
        return newAddPage();
    }

    /**
     * Opens the currently selected voucher for edit via the Actions→Edit path — clicks "Edit" in
     * the already-open Actions dropdown (see {@link #clickActions()}) and returns the opened
     * form.
     */
    @LogStep("Open Voucher for Edit via Actions")
    public TAddPage clickEditFromActions() {
        mainGrid.clickEdit();
        return newAddPage();
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
        return newAddPage();
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
