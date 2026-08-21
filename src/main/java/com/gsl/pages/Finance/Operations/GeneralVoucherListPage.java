package com.gsl.pages.Finance.Operations;

import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListPageBase;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherListFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherListGridHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherMainGrid;

/**
 * General Voucher list page — plan §17: thin domain subclass of the common
 * {@link VoucherListPageBase}. Public API is unchanged from before this split —
 * {@code NavigationUtils} still instantiates this class reflectively via its public no-arg
 * constructor, and every method signature tests already call is preserved (inherited from the
 * base rather than declared here, but the same signatures/return types).
 *
 * <p>Only what's genuinely GV-specific stays here: the Revert-flow composites
 * ({@link #clickRevert()}/{@link #confirmRevert()}/{@link #revertSelectedDocument()}) —
 * {@code confirmRevert()}'s dialog-text matching is confirmed only against GV's live DOM (plan
 * §12), and its return type ({@link GeneralVoucherRevertOutcome}) is itself a GV-named enum.
 * Everything else ({@code clickAdd}/{@code searchByVoucherNo}/{@code clickEditForVoucher}/
 * {@code clickEditFromActions}/{@code doubleClickToEdit}/{@code clickDeleteForVoucher}/
 * {@code clickUnpost}/{@code confirmDelete}/{@code clickActions}/{@code clickRefresh}/
 * {@code clickClearFilter}/{@code clickNextPage}/{@code selectDocumentInListPage}) is inherited.
 */
public class GeneralVoucherListPage extends VoucherListPageBase<
        GeneralVoucherListGridHeader, GeneralVoucherMainGrid, GeneralVoucherListFooter,
        GeneralVoucherAdd, GeneralVoucherListPage> {

    public GeneralVoucherListPage() {
        super(new GeneralVoucherListGridHeader(), new GeneralVoucherMainGrid(), new GeneralVoucherListFooter());
    }

    @Override
    protected GeneralVoucherAdd newAddPage() {
        return new GeneralVoucherAdd();
    }

    // ── Revert from List Page (GV-specific — plan §10/§12) ───────────────────────

    /**
     * Clicks "Revert" in the open Actions dropdown — step 3 of the Revert-from-List-Page flow.
     * Does NOT confirm the follow-up Yes/No dialog — call {@link #confirmRevert()} separately.
     */
    public GeneralVoucherListPage clickRevert() {
        getMainGrid().clickRevert();
        return this;
    }

    /**
     * Confirms whichever post-Revert dialog appears — step 4 (completes the operation). Returns
     * which case actually happened (Posted → un-posted vs. already un-posted, no-op) so the
     * caller can assert the correct expected DB outcome.
     */
    public GeneralVoucherRevertOutcome confirmRevert() {
        return getMainGrid().confirmRevert();
    }

    /**
     * Composite convenience over {@link #clickActions()}/{@link #clickRevert()}/
     * {@link #confirmRevert()} — plan §10. General Voucher has no standalone "Unpost" button;
     * un-posting a document IS the Revert action, so this is the one method a caller reaches
     * for whenever it just needs a Posted document un-posted (e.g. {@code EditGeneralVoucher}'s
     * "Unpost if Posted" step), without re-deriving the three-call sequence inline. Assumes the
     * target document's row is already selected/checked in the list — call
     * {@link #selectDocumentInListPage(String)} first, same as the Revert-from-List-Page flow.
     *
     * <p>Deliberately NOT used by {@link com.tests.Finance.Operations.Vouchers.RevertGeneralVoucher}
     * itself — that test interposes a diagnostic DB re-read between the Actions and Revert
     * clicks for its own race-condition checks, which this collapsed method has no room for.
     */
    public GeneralVoucherRevertOutcome revertSelectedDocument() {
        clickActions();
        clickRevert();
        return confirmRevert();
    }
}
