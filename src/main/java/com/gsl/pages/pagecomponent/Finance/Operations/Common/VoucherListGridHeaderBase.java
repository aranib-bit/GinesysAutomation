package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

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
     * The open Actions dropdown's own floating menu container — same confirmed-live
     * {@code x-menu}/{@code aria-hidden='false'} idiom {@code GridColumnMenu.OPEN_MENU} already
     * uses for the identical "is a floating ExtJS menu genuinely open right now?" question
     * (that class's own comment notes ExtJS keeps a stale, previously-shown menu instance around
     * via {@code visibility:hidden} rather than removing it, so a plain "last x-menu in DOM
     * order" pick can resolve to the wrong one — {@code aria-hidden} is the semantic signal that
     * isn't tied to any one particular CSS-hiding mechanism).
     */
    private static final By OPEN_ACTIONS_MENU = By.xpath(
            "(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
            + "[@aria-hidden='false'])[last()]");

    /**
     * Opens the list page's "Actions" dropdown, and waits for its menu container to actually
     * render before returning — {@code SeleniumUtils.click} on the Actions button only confirms
     * the click landed, not that the resulting dropdown has finished rendering its items.
     * Without this, a caller that polls for one specific item's enabled state
     * (e.g. {@code VoucherMainGridBase.clickGridAction}) immediately after this method returns
     * could be racing the menu's OWN render/bind cycle rather than the grid's row-selection
     * state — a different, previously-unexamined explanation for
     * "Actions→Edit/Delete is disabled" failures confirmed live across multiple runs
     * (20260901_170830, 20260901_185011) where the checkbox visibly registered as selected yet
     * the specific menu item stayed disabled through several retry windows.
     */
    @LogStep("Click Actions")
    public void clickActions() {
        SeleniumUtils.click(actionsButtonLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_ACTIONS_BUTTON");
        WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                .until(ExpectedConditions.visibilityOfElementLocated(OPEN_ACTIONS_MENU));
    }

    /** Clicks "Clear Filter" in the list page's header toolbar. */
    @LogStep("Click Clear Filter")
    public void clickClearFilter() {
        SeleniumUtils.click(clearFilterLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_CLEAR_FILTER");
    }
}
