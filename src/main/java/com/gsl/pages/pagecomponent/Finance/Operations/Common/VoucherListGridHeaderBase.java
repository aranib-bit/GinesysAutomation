package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Common list-page toolbar/header region shared by AR, AP, GV (and future Contra) voucher
 * list pages — the title bar (e.g. "General Vouchers") plus its Add/Actions/Clear Filter/
 * date-range-filter controls, as seen in the top region of the list page screenshot.
 * Subclasses pass their {@link VoucherDomain} to the constructor; the expected page-header
 * text and log tags are derived automatically from the domain.
 *
 * <p>Locators are exposed as {@code protected} methods rather than {@code private static final}
 * fields so a domain subclass can override just one of them if that list page's DOM ever
 * diverges, without having to fork this whole class.
 */
public abstract class VoucherListGridHeaderBase {

    private final VoucherDomain domain;
    private final String expectedPageHeader;

    protected VoucherListGridHeaderBase(VoucherDomain domain) {
        this.domain = domain;
        this.expectedPageHeader = domain.getListPageHeader();
    }

    /** Confirmed against the live DOM (General Voucher list) — a bare span, not scoped to a toolbar. */
    protected By actionsButtonLocator() {
        return By.xpath("//span[text()='Actions']");
    }

    /** Confirmed against the live DOM (AR/General Voucher list) — a bare span, not scoped to a toolbar. */
    protected By clearFilterLocator() {
        return By.xpath("//span[@data-ref='btnInnerEl' and normalize-space()='Clear Filter']");
    }

    /**
     * Confirmed against the live DOM (AR Voucher list) — scoped to THIS list page's own toolbar
     * (via {@link VoucherDomain#getListPageHeaderEnum()}) so it can't collide with an "Add"
     * button belonging to another toolbar elsewhere on the page.
     */
    protected By addButtonLocator() {
        return domain.getListPageHeaderEnum().scopedToolbarButtonLocator("Add");
    }

    /**
     * Confirms the browser is actually showing this voucher type's list page right now, via
     * the page-header text — same live-DOM-signal pattern used across the framework
     * (replaces each list page's former private {@code EXPECTED_PAGE_HEADER} constant).
     */
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        if (!expectedPageHeader.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Expected to be on the " + domain.getVoucherLabel() + " list (page header '"
                            + expectedPageHeader + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    /**
     * Clicks the "Add" button. Named {@code clickAddButton()} rather than {@code clickAdd()} and
     * returns {@code void} — the facade page calls this then constructs its own domain-specific
     * Add-page return type ({@code GeneralVoucherAdd}/{@code ArVoucherAdd}/{@code ApVoucherAdd}),
     * mirroring how {@code VoucherSubLedgerInformationBase#createPaymentPopup()} solves the same
     * divergent-return-type problem on the Add-page side.
     */
    public final void clickAddButton() {
        SeleniumUtils.click(addButtonLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_ADD");
    }

    /**
     * Opens the list page's "Actions" dropdown.
     *
     * <p>A previous version of this method also waited here for "any open {@code x-menu}"
     * (mirroring {@code GridColumnMenu.OPEN_MENU}'s idiom) before returning, on the theory that
     * a caller polling a specific item's enabled state right after this call could be racing the
     * menu's own render cycle. That wait turned out to be exactly the risk
     * {@code GridColumnMenu.OPEN_MENU}'s own Javadoc already warns about for its column-filter
     * flyouts: ExtJS keeps a previously-shown menu instance around via {@code visibility:hidden}
     * rather than removing it, so an unscoped "last {@code aria-hidden='false'} x-menu" pick can
     * resolve to a stale, UNRELATED menu — e.g. a leftover "Voucher No." exact-match filter
     * flyout from the row-selection step that runs just before this call — rather than genuinely
     * confirming the Actions dropdown rendered anything. Confirmed live (run 20260902_121853):
     * the wait added zero measurable effect on the "Actions→Edit/Delete is disabled" failures it
     * was meant to fix. Removed; the real fix is a PRECISE, action-specific render-wait in
     * {@link VoucherMainGridBase#clickGridAction}, which knows which item it's actually polling
     * for and isn't vulnerable to this ambiguity.
     */
    @LogStep("Click Actions")
    public void clickActions() {
        SeleniumUtils.click(actionsButtonLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_ACTIONS_BUTTON");
    }

    /** Clicks "Clear Filter" in the list page's header toolbar. */
    @LogStep("Click Clear Filter")
    public void clickClearFilter() {
        SeleniumUtils.click(clearFilterLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_CLEAR_FILTER");
    }
}
