package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.utils.Log;
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

    /** Exposes this list page's domain — e.g. so {@link VoucherListPageBase} can build a
     *  human-readable label (via {@link VoucherDomain#getVoucherLabel()}) for verification-status
     *  report entries, without duplicating the domain reference on the facade itself. */
    public VoucherDomain getDomain() {
        return domain;
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
     * The Actions button while its dropdown is open. Confirmed against captured DOM: ExtJS adds
     * {@code x-btn-menu-active} to the button's own {@code <a>} exactly while its menu is showing
     * (present once in the open capture, absent in both closed captures) — the most precise
     * open/closed signal available, and it belongs to THIS button rather than to whatever menu
     * happens to be floating in the document.
     *
     * <p>Deliberately anchored on the button, not on "an open {@code x-menu}": an earlier version
     * of this method waited on a generic {@code (//div[…x-menu…][@aria-hidden='false'])[last()]},
     * which is exactly the ambiguity {@code GridColumnMenu.OPEN_MENU}'s own Javadoc warns about —
     * ExtJS keeps previously-shown menus in the DOM via {@code visibility:hidden} rather than
     * removing them, and the row-search step immediately preceding this call leaves its own
     * "Exact match…" filter flyout behind. That generic wait could be satisfied by the stale
     * flyout and had zero measurable effect on the failures it was meant to fix
     * (confirmed live, run 20260902_121853).
     */
    protected By actionsButtonMenuActiveLocator() {
        return By.xpath("//span[text()='Actions']/ancestor::a[1][contains(@class,'x-btn-menu-active')]");
    }

    /**
     * Opens the list page's "Actions" dropdown and confirms it actually opened.
     *
     * <p>{@code SeleniumUtils.click} only confirms the click landed on a clickable element — not
     * that the dropdown opened. That matters because the ExtJS menu is <b>lazily rendered</b>: the
     * {@code actionmenu} container does not exist in the DOM at all until the first successful
     * Actions click (the same lazy-render behaviour that makes the Logout menu item unfindable in
     * {@code HomePage} until the profile menu is opened). If the click is swallowed — e.g. by a
     * lingering modal/loading mask — nothing throws, and the caller then fails much later with a
     * confusing message about the menu item instead.
     *
     * <p>So: click, then verify via {@link #actionsButtonMenuActiveLocator()}, and re-click once if
     * it didn't take. One bounded retry, checking the real DOM condition each time.
     */
    @LogStep("Click Actions")
    public void clickActions() {
        SeleniumUtils.click(actionsButtonLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_ACTIONS_BUTTON");

        int shortWait = (int) ConfigFactory.getWaitConfig().shortWaitSeconds();
        if (!SeleniumUtils.isElementPresent(actionsButtonMenuActiveLocator(), shortWait)) {
            Log.warn("{}: the Actions dropdown did not open on the first click (no x-btn-menu-active on "
                    + "the button) — re-clicking once", domain.name());
            SeleniumUtils.click(actionsButtonLocator(), WaitType.CLICKABLE,
                    domain.name() + "_VOUCHER_ACTIONS_BUTTON_RETRY");
            // Not asserted here: clickGridAction's own scoped item lookup is the authoritative
            // check and produces a far better diagnostic (plus a DOM capture) if the menu still
            // isn't open. Failing here too would only duplicate that with less context.
        }
    }

    /** Clicks "Clear Filter" in the list page's header toolbar. */
    @LogStep("Click Clear Filter")
    public void clickClearFilter() {
        SeleniumUtils.click(clearFilterLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_CLEAR_FILTER");
    }
}
