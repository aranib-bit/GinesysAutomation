package com.gsl.pages.Finance.Operations;

import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherRevertOutcome;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherListFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherListGridHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherMainGrid;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListPageBase;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;

/**
 * AP Voucher list page — plan §17: thin domain subclass of the common
 * {@link VoucherListPageBase}, mirroring {@code GeneralVoucherListPage}. Public API is
 * unchanged from before this split — {@code NavigationUtils} still instantiates this class
 * reflectively via its public no-arg constructor, and every method signature tests already call
 * is preserved (inherited from the base rather than declared here, but the same signatures/
 * return types) — including {@code selectDocumentInListPage(String)}, which previously existed
 * only on {@code GeneralVoucherListPage} and is now available here too, closing that gap.
 * Locators remain the same unresolved {@code //TODO-} placeholders as before (living in
 * {@code VoucherMainGridBase}, not duplicated locally).
 *
 * <p>The Revert-flow composites below ({@link #clickRevert()}/{@link #confirmRevert()}/
 * {@link #revertSelectedDocument()}) are new, added for {@code RevertApVoucher} — copied verbatim
 * in shape from {@link GeneralVoucherListPage}'s equivalents, substituting
 * {@link APVoucherRevertOutcome} for {@code GeneralVoucherRevertOutcome}.
 */
public class ApVoucherListPage extends VoucherListPageBase<
        APVoucherListGridHeader, APVoucherMainGrid, APVoucherListFooter,
        ApVoucherAdd, ApVoucherListPage> {

    public ApVoucherListPage() {
        super(new APVoucherListGridHeader(), new APVoucherMainGrid(), new APVoucherListFooter());
    }

    @Override
    protected ApVoucherAdd newAddPage() {
        return new ApVoucherAdd();
    }

    // ── Revert from List Page (new — mirrors GeneralVoucherListPage's equivalents) ────

    /**
     * Clicks "Revert" in the open Actions dropdown — step 3 of the Revert-from-List-Page flow.
     * Does NOT confirm the follow-up Yes/No dialog — call {@link #confirmRevert()} separately.
     */
    public ApVoucherListPage clickRevert() {
        getMainGrid().clickRevert();
        return this;
    }

    /**
     * Document-number-aware overload — mirrors {@link VoucherListPageBase#clickEditFromActions(
     * String, DocumentSearchStrategy)}'s reselect-and-retry shape, applied to Revert exactly as
     * {@code GeneralVoucherListPage#clickRevert(String, DocumentSearchStrategy)} already does: on
     * a lost/never-opened Actions dropdown, re-selects {@code documentNumber} and re-opens Actions
     * once before giving up, instead of {@link #clickRevert()}'s one-shot check of the same
     * already-lost state.
     */
    public ApVoucherListPage clickRevert(String documentNumber, DocumentSearchStrategy strategy) {
        Runnable reselect = () -> {
            selectDocumentFast(documentNumber, strategy);
            clickActions();
        };
        getMainGrid().clickRevert(reselect);
        return this;
    }

    /**
     * Confirms whichever post-Revert dialog appears — step 4 (completes the operation). Returns
     * which case actually happened so the caller can assert the correct expected DB outcome.
     */
    public APVoucherRevertOutcome confirmRevert() {
        return getMainGrid().confirmRevert();
    }

    /**
     * Composite convenience over {@link #clickActions()}/{@link #clickRevert()}/
     * {@link #confirmRevert()} — mirrors {@code GeneralVoucherListPage#revertSelectedDocument()}.
     * AP Voucher has no standalone "Unpost" button either — un-posting a document IS the Revert
     * action, so this is the one method a caller reaches for whenever it just needs a Posted
     * document un-posted (e.g. {@code EditApVoucher}'s UI-driven sibling would use this, though
     * {@code EditApVoucher} itself unposts via the API per the user's explicit instruction).
     * Assumes the target document's row is already selected/checked in the list.
     */
    public APVoucherRevertOutcome revertSelectedDocument() {
        clickActions();
        clickRevert();
        return confirmRevert();
    }
}
