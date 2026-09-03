package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.section.HtmlCaptureSection;
import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherActionsMenu;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridAction;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.google.common.util.concurrent.Uninterruptibles;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Common list-page data-grid region shared by AR, AP, GV (and future Contra) voucher list
 * pages — the column headers + data rows + row-checkbox column, as seen in the middle
 * region of the list page screenshot.
 *
 * <p>Owns the one genuinely proven piece of pagination-handling in the list-page area —
 * {@link #selectDocumentInListPage(String)} — so AR/AP get it for free instead of
 * duplicating it later, plus the still-open {@code //TODO-} placeholder locators for
 * search/edit/delete/unpost (pre-existing debt, relocated here rather than duplicated
 * three times).
 */
public abstract class VoucherMainGridBase {

    /** Short existence-check timeout for enabled/disabled state probes — same idiom
     *  {@link VoucherListFooterBase} already uses for its own toolbar-button state checks. */
    private static final int ACTION_STATE_CHECK_TIMEOUT_SECONDS = 2;

    /** Short poll timeout for confirming a checkbox click actually registered as checked. */
    private static final int CHECKBOX_STATE_CHECK_TIMEOUT_SECONDS = 2;
    private static final long CHECKBOX_STATE_POLL_INTERVAL_MS = 100L;

    /** TODO: confirm against the live UI — best-effort guess, not yet verified (pre-existing debt). */
    private static final By SEARCH_BOX     = By.xpath("//TODO-search-input");
    private static final By SEARCH_BUTTON  = By.xpath("//TODO-search-button");
    private static final By EDIT_ICON      = By.xpath("//TODO-edit-icon-first-row");
    private static final By UNPOST_BUTTON  = By.xpath("//TODO-unpost-button");

    /**
     * TODO: entirely UNCONFIRMED — unlike this class's other {@code //TODO-} locators (which have
     * at least a real, named UI element to eventually locate), {@code CommonVoucherActionsMenu
     * .RELEASE} has never been clicked by any test in this codebase. This placeholder assumes a
     * single OK/Yes-style confirm button follows the click, mirroring {@link #confirmDelete()}'s
     * now-confirmed Yes/No dialog shape — that assumption itself is unverified. See
     * {@link #confirmRelease()}'s Javadoc.
     */
    private static final By CONFIRM_RELEASE = By.xpath("//TODO-confirm-release-button");

    private final VoucherDomain domain;

    protected VoucherMainGridBase(VoucherDomain domain) {
        this.domain = domain;
    }

    /**
     * Row checkbox for a given document number — confirmed against the live DOM. Instance
     * (not static) method so a domain subclass can override it if a specific grid's checkbox
     * markup ever diverges, without forking the whole class.
     */
    protected By checkboxLocator(String documentNumber) {
        return By.xpath("//div[normalize-space()='" + documentNumber
                + "']/ancestor::tr[1]//div[contains(@class,'x-grid-checkcolumn')]");
    }

    /** The document-number cell itself — same anchor text {@link #checkboxLocator} walks up
     *  from, exposed separately so {@link #clickCheckbox} can also click it directly (see that
     *  method's Javadoc for why). */
    private By rowAnchorLocator(String documentNumber) {
        return By.xpath("//div[normalize-space()='" + documentNumber + "']");
    }

    @LogStep("Search Voucher by Document Number")
    public void searchByVoucherNo(String voucherNo) {
        SeleniumUtils.sendKeys(SEARCH_BOX, WaitType.VISIBLE, voucherNo, "SEARCH_BOX");
        SeleniumUtils.click(SEARCH_BUTTON, WaitType.CLICKABLE, "SEARCH_BUTTON");
    }

    /**
     * Clicks the Edit icon on the first (expected: only) row after a search. Named
     * {@code clickEditIcon()} rather than {@code clickEditForVoucher()} and returns {@code void}
     * — every domain's Add/edit page is a different concrete type, so the facade page calls this
     * then constructs its own return type (same divergent-return-type fix as
     * {@code VoucherListGridHeaderBase#clickAddButton()}).
     */
    public final void clickEditIcon() {
        SeleniumUtils.click(EDIT_ICON, WaitType.CLICKABLE, "EDIT_ICON");
    }

    /**
     * Clicks "Delete" in the open Actions dropdown — reuses the same proven
     * {@link #clickGridAction} mechanism {@link #clickEdit()}/{@link #clickRevert()} already use
     * (real, generic, label-driven locator + explicit enabled-check), rather than the previous
     * permanent placeholder ({@code //TODO-delete-icon-first-row}, which could never have
     * matched anything). Confirmed live 2026-09-01 via screenshot: the Actions dropdown's
     * "Delete" item is exactly {@code //span[text()='Delete']} — {@link
     * CommonVoucherActionsMenu#DELETE}'s default {@link IVoucherGridAction#locator()} — and it is
     * only enabled once the target row's checkbox is checked (the same screenshot's earlier frame
     * showed "Delete" visibly greyed out with no row selected, matching {@link #clickGridAction}'s
     * own "is a row selected?" diagnostic).
     */
    @LogStep("Click Delete for Voucher")
    public void clickDeleteForVoucher() {
        clickGridAction(CommonVoucherActionsMenu.DELETE.resolveFor(domain).orElseThrow());
    }

    /** Clicks the Unpost button for a posted ({@code release_status='P'}) voucher. */
    @LogStep("Unpost Voucher")
    public void clickUnpost() {
        SeleniumUtils.click(UNPOST_BUTTON, WaitType.CLICKABLE, "UNPOST_BUTTON");
    }

    /**
     * Confirms the delete confirmation dialog (Yes), then dismisses the follow-up "Result"
     * dialog (OK) — mirrors {@code GeneralVoucherMainGrid.confirmRevert()}'s two-dialog handling
     * pattern via the shared {@link DocumentMessageBox}/{@link CommonButton#YES} mechanism,
     * replacing the previous permanent placeholder ({@code //TODO-confirm-delete-button}).
     * Confirmed live 2026-09-01 via screenshot: a "Delete General Voucher" dialog reading
     * "Document once deleted cannot be retrieved. Do you want to delete this document?" with
     * Yes/No buttons (see {@link DocumentMessageBoxMessage#DELETE_CONFIRMATION}), followed by a
     * separate "Result" dialog reading "Document No: &lt;docno&gt; - Deleted Successfully." with
     * a single OK button (see {@link DocumentMessageBoxMessage#DELETE_SUCCESS_RESULT}) — clicking
     * OK returns to the List Page. The Result dialog MUST be dismissed here, not left open — an
     * un-dismissed dialog blocks the grid for whatever runs next, the same bug
     * {@code confirmRevert()}'s own Javadoc documents for {@code REVERT_SUCCESS_RESULT}.
     */
    @LogStep("Confirm Delete Dialog")
    public void confirmDelete() {
        DocumentMessageBox msgBox = new DocumentMessageBox();
        msgBox.verifyMessage(DocumentMessageBoxMessage.DELETE_CONFIRMATION);
        SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE,
                domain.name() + "_DELETE_CONFIRM_YES");
        JavascriptExecutorUtils.waitForExtJS();

        DocumentMessageBox resultBox = new DocumentMessageBox();
        resultBox.verifyMessage(DocumentMessageBoxMessage.DELETE_SUCCESS_RESULT);
        resultBox.clickOk();
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Clicks a resolved {@link IVoucherGridAction} in the open Actions dropdown — one shared
     * click method for every domain's grid (plan §9.3), same style as {@link #clickDeleteForVoucher()}/
     * {@link #clickUnpost()}. {@link #clickEdit()}/{@link #clickRevert()} are thin named
     * wrappers delegating to this.
     *
     * <p>Checks {@link IVoucherGridAction#itemInOpenMenuLocator()} — open dropdown + this item
     * genuinely enabled, per that locator's own Javadoc on why the enabled-check is back after an
     * intermediate version dropped it — then clicks the item's plain text {@code <span>}
     * ({@link IVoucherGridAction#locator()}), not the {@code <a>} the enabled-check resolves to;
     * that anchor carries {@code href="#"} and would add a hash-navigation side effect on click.
     *
     * <p>If the check fails, the message distinguishes the two situations DOM forensics showed are
     * genuinely different (see {@link IVoucherGridAction#itemInOpenMenuLocator()}): the dropdown
     * may simply not be open (nothing to click yet), or it may be open with this item confirmed
     * {@code aria-disabled="true"} — a real "no row selected" state, not a locator artifact. The
     * actual fix for the latter lives at the call sites that select a row before opening this menu
     * (see {@code DeleteGeneralVoucher}/{@code EditGeneralVoucher}'s re-select-immediately-
     * before-Actions steps) — this method's job is only to report which situation it hit, clearly.
     */
    @LogStep("Click Grid Action")
    public void clickGridAction(IVoucherGridAction action) {
        By itemEnabledInOpenMenu = action.itemInOpenMenuLocator();
        if (!SeleniumUtils.isElementPresent(itemEnabledInOpenMenu, ACTION_STATE_CHECK_TIMEOUT_SECONDS)) {
            // One bounded re-check: clickActions() already verifies the dropdown opened, and the
            // caller may have just re-selected the row a moment ago — the enabled-state binding
            // can lag that by a little. Re-reading the same real condition is cheap and honest,
            // not a blind timeout inflation.
            Log.warn("{}VoucherMainGrid: Actions→{} not found enabled in an open actionmenu on first "
                            + "check — re-checking once before failing", domain.name(), action.label());
            if (!SeleniumUtils.isElementPresent(itemEnabledInOpenMenu, ACTION_STATE_CHECK_TIMEOUT_SECONDS)) {
                boolean menuOpen = SeleniumUtils.isElementPresent(
                        By.xpath("//div[contains(@class,'actionmenu')][@aria-expanded='true']"),
                        1);
                new HtmlCaptureSection(DriverManager.getDriver(),
                        "ActionsMenu_" + domain.name() + "_" + action.label());
                throw new IllegalStateException(domain.name() + "VoucherMainGrid: Actions→" + action.label()
                        + " "
                        + (menuOpen
                            ? "is disabled — is a row selected? (confirmed: the dropdown is open but "
                              + "this item's aria-disabled='true')"
                            : "— the Actions dropdown is not open at all")
                        + ". Diagnostics: " + JavascriptExecutorUtils.getGridDiagnostics());
            }
        }
        SeleniumUtils.click(action.locator(), WaitType.CLICKABLE, domain.name() + "_ACTION_" + action.label());
    }

    /**
     * Clicks "Revert" in the open Actions dropdown (plan §12 — moved here from
     * {@code GeneralVoucherMainGrid}, which had it as a domain-hardcoded copy; the only
     * GV-specific part was {@code VoucherDomain.GV}, replaced here with this instance's own
     * {@link #domain}). Common to every domain's grid, since {@code Revert} is one of
     * {@link CommonVoucherActionsMenu}'s 7 shared actions. Does NOT confirm the follow-up
     * dialog(s) — {@code GeneralVoucherMainGrid.confirmRevert()} stays domain-specific (its
     * dialog-text matching is confirmed only against GV's live DOM — see plan §12).
     */
    @LogStep("Click Revert")
    public void clickRevert() {
        clickGridAction(CommonVoucherActionsMenu.REVERT.resolveFor(domain).orElseThrow());
    }

    /**
     * Clicks "Edit" in the open Actions dropdown — the Actions→Edit entry path (plan §9.2/9.3,
     * relocated here per §12 for the same reason as {@link #clickRevert()}).
     */
    @LogStep("Click Edit")
    public void clickEdit() {
        clickGridAction(CommonVoucherActionsMenu.EDIT.resolveFor(domain).orElseThrow());
    }

    /**
     * Clicks "Release" in the open Actions dropdown — scaffold only (see the "GV Revert picks
     * documents in a now-closed accounting period, + closed-period negative-test coverage" plan,
     * Part D). The click mechanism itself reuses {@link #clickGridAction}, the SAME
     * already-proven Actions-dropdown path {@link #clickRevert()}/{@link #clickEdit()} use — that
     * part is NOT a guess. What genuinely IS unconfirmed: whether "Release" is even the
     * Unposted→Posted counterpart to Revert (the working theory this scaffold is built around),
     * what dialog(s) follow the click, and what their exact text is — none of that is asserted
     * anywhere in this codebase yet. Does NOT confirm the follow-up dialog —
     * {@link #confirmRelease()} is a separate, even-less-confirmed stub; call it only once its
     * own TODO locator has been confirmed live.
     */
    @LogStep("Click Release")
    public void clickRelease() {
        clickGridAction(CommonVoucherActionsMenu.RELEASE.resolveFor(domain).orElseThrow());
    }

    /**
     * Confirms whichever dialog follows a Release click — scaffold only, mirroring
     * {@link #confirmDelete()}'s single-button shape as a best-effort guess, NOT a confirmed
     * behavior. Unlike {@link com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherMainGrid#confirmRevert()}
     * (which reads the dialog text and returns a real, live-confirmed outcome), this method asserts nothing about what
     * actually happens — it exists purely so the click sequence is ready to wire up once a real
     * discovery pass confirms: (1) {@link #CONFIRM_RELEASE}'s actual locator, (2) how many
     * dialog(s) actually appear (Revert's own confirm flow needed two — a Yes/No THEN a separate
     * Result dialog — Release may need the same), and (3) their exact text, so a
     * {@code GeneralVoucherReleaseOutcome}-style enum (mirroring
     * {@link com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome})
     * can be built against real evidence instead of guessed text.
     */
    @LogStep("Confirm Release Dialog")
    public void confirmRelease() {
        SeleniumUtils.click(CONFIRM_RELEASE, WaitType.CLICKABLE, "CONFIRM_RELEASE");
    }

    /**
     * Selects the given document's row checkbox in the list grid.
     *
     * <p>Handles the pagination/virtual-scroll problem
     * {@link com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup#selectDocument}
     * already solved for the on-account "select document" popup grid: when the target row
     * isn't rendered in the currently-loaded viewport, falls back to a direct jump, then a
     * trusted mouse-wheel scroll loop (terminating on content-based end-of-list, not a fixed
     * attempt count), then an ExtJS {@code focusRow} as a last resort. Reuses that same
     * {@code JavascriptExecutorUtils}/{@code ActionUtils} machinery and the same tuned
     * {@code selectdoc.*} wait-config buffers — they are generic ExtJS BufferedStore
     * scroll/render timings, not domain-specific, so this works identically for AR/AP/GV.
     *
     * @param documentNumber the {@code scheme_docno} to select (e.g. {@code "VG/00304/26-27"})
     */
    @LogStep("Select Voucher in List Page")
    public void selectDocumentInListPage(String documentNumber) {
        WebElement checkbox = locateCheckboxOnCurrentPage(documentNumber);
        if (checkbox == null) {
            throw new NoSuchElementException(
                    domain.name() + "VoucherMainGrid: Checkbox for [" + documentNumber
                    + "] not found after direct jump + mouse-wheel scroll to end-of-list + focusRow fallback. Diagnostics: "
                    + JavascriptExecutorUtils.getGridDiagnostics());
        }
        clickCheckbox(documentNumber);
    }

    /**
     * Non-throwing sibling of {@link #selectDocumentInListPage(String)} — searches only the
     * currently loaded page (same direct-find/jump/scroll/focusRow strategies), and clicks the
     * checkbox if found. Returns {@code false} instead of throwing when the document genuinely
     * isn't on this page, so a caller with pagination awareness (e.g.
     * {@code GeneralVoucherListPage.selectDocumentInListPage}, which also knows about the
     * footer's Next Page control) can try another page rather than treating "not on this page"
     * as a hard failure. {@code public} (not {@code protected}) because that caller is the
     * facade page, not a subclass — it reaches this through the {@code mainGrid} field, and
     * protected access doesn't extend to a same-hierarchy field held by an unrelated class.
     *
     * @return {@code true} if the checkbox was found and clicked, {@code false} if the document
     *         isn't present on the currently loaded page
     */
    public boolean selectDocumentIfPresentOnCurrentPage(String documentNumber) {
        WebElement checkbox = locateCheckboxOnCurrentPage(documentNumber);
        if (checkbox == null) {
            return false;
        }
        clickCheckbox(documentNumber);
        return true;
    }

    /**
     * Double-clicks the given document's row to open it for edit — the second live entry path
     * the app supports besides Actions→Edit (plan §9.4). Searches only the currently loaded
     * page — callers needing pagination should call
     * {@link VoucherGridPaginationFinder#doubleClickRowAcrossPages} instead (as
     * {@code VoucherListPageBase#doubleClickToEdit} does), the double-click analogue of
     * {@link #selectDocumentInListPage(String)}/{@link VoucherGridPaginationFinder#selectDocumentAcrossPages}.
     *
     * @param documentNumber the {@code scheme_docno} to open (e.g. {@code "VG/00304/26-27"})
     * @throws NoSuchElementException if the document isn't found on the currently loaded page
     */
    @LogStep("Double-Click Row to Open")
    public void doubleClickRowToOpen(String documentNumber) {
        if (!doubleClickRowIfPresentOnCurrentPage(documentNumber)) {
            throw new NoSuchElementException(
                    domain.name() + "VoucherMainGrid: Row for [" + documentNumber
                    + "] not found after direct jump + mouse-wheel scroll to end-of-list + focusRow fallback. Diagnostics: "
                    + JavascriptExecutorUtils.getGridDiagnostics());
        }
    }

    /**
     * Non-throwing sibling of {@link #doubleClickRowToOpen(String)} — searches only the
     * currently loaded page (same direct-find/jump/scroll/focusRow strategies via
     * {@link #locateCheckboxOnCurrentPage}), double-clicking the row if found. Returns
     * {@code false} instead of throwing when the document genuinely isn't on this page, so a
     * caller with pagination awareness ({@link VoucherGridPaginationFinder#doubleClickRowAcrossPages})
     * can turn to another page rather than treating "not on this page" as a hard failure — the
     * same split {@link #selectDocumentIfPresentOnCurrentPage(String)} already has relative to
     * {@link #selectDocumentInListPage(String)}.
     *
     * <p>Added after a live run confirmed {@code DOUBLE_CLICK_EDIT} entry-method scenarios could
     * never find a claimed voucher sitting past page 1 of a 35-page grid — this method (used via
     * {@link VoucherGridPaginationFinder#doubleClickRowAcrossPages}) is what was missing, since
     * {@code doubleClickRowToOpen} on its own never had any page-turning behind it.
     *
     * @return {@code true} if the row was found and double-clicked, {@code false} if the
     *         document isn't present on the currently loaded page
     */
    public boolean doubleClickRowIfPresentOnCurrentPage(String documentNumber) {
        WebElement checkbox = locateCheckboxOnCurrentPage(documentNumber);
        if (checkbox == null) {
            return false;
        }
        WebElement row = checkbox.findElement(By.xpath("./ancestor::tr[1]"));
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsMinTimeout())
                .until(ExpectedConditions.visibilityOf(row));
        ActionUtils.doubleClick(row);
        JavascriptExecutorUtils.waitForExtJS();
        return true;
    }

    /**
     * Locates the given document's row checkbox on the currently loaded page only — direct find
     * → direct jump → trusted mouse-wheel scroll loop (content-based end-of-list, not a fixed
     * attempt count) → ExtJS {@code focusRow} fallback. Returns {@code null} (never throws) when
     * none of those strategies find it on this page — the two public methods above decide what
     * "not found on this page" means for their caller.
     */
    private WebElement locateCheckboxOnCurrentPage(String documentNumber) {
        WebDriver driver = DriverManager.getDriver();
        By checkboxBy = checkboxLocator(documentNumber);

        WebElement checkbox;
        try {
            checkbox = driver.findElement(checkboxBy);
            Log.debug("{}VoucherMainGrid: Checkbox found directly in viewport for document [{}]", domain.name(), documentNumber);
            return checkbox;
        } catch (NoSuchElementException e) {
            Log.warn("{}VoucherMainGrid: Checkbox not in viewport for [{}] — attempting direct jump", domain.name(), documentNumber);
            checkbox = null;
        }

        // Strategy 1: direct jump — works for any record count when the store has the row loaded.
        long jumpedTo = JavascriptExecutorUtils.jumpGridToDocument(documentNumber);
        if (jumpedTo >= 0) {
            Uninterruptibles.sleepUninterruptibly(
                    ConfigFactory.getWaitConfig().selectDocJumpRenderMs(), TimeUnit.MILLISECONDS);
            try {
                checkbox = driver.findElement(checkboxBy);
            } catch (NoSuchElementException ex) {
                Log.warn("{}VoucherMainGrid: Direct jump landed but checkbox still absent for [{}] — falling back to scroll",
                        domain.name(), documentNumber);
            }
        }

        // Strategy 2: trusted mouse-wheel scroll loop for BufferedStore grids — terminates on
        // content-based end-of-list, independently RE-CONFIRMED (see
        // WaitConfig#selectDocScrollNoProgressConfirmWaitMs()'s Javadoc) rather than trusted off
        // a single quick read, so a slow async page-fetch on a live/staging server isn't mistaken
        // for genuinely having no more rows (confirmed root cause of EditGeneralVoucher's
        // paginated search scanning every page of a 35-page result set and still failing to find
        // a document that was really just further down a page abandoned too early).
        if (checkbox == null) {
            WebElement gridView = JavascriptExecutorUtils.getActiveGridViewElement();
            Map<String, Object> prev = JavascriptExecutorUtils.getGridScrollProgress();
            long prevScrollTop   = readLong(prev, "scrollTop", 0L);
            long clientHeight    = readLong(prev, "clientHeight", 360L);
            long prevStoreCount  = readLong(prev, "storeCount", -1L);
            String prevTopDoc    = readString(prev, "topDocNumber");
            String prevBottomDoc = readString(prev, "bottomDocNumber");

            // NOT a hardcoded literal, despite how it reads in a "scrollByWheel deltaY=557"-style
            // log line — clientHeight above is JS-measured live from the actual grid viewport on
            // this call (falling back to 360 only if that probe returns nothing), so this scales
            // correctly with the real rendered viewport height and will not fail at a different
            // screen resolution.
            int deltaY         = (int) Math.max(200L, clientHeight);
            int safetyCap      = ConfigFactory.getWaitConfig().selectDocScrollSafetyCap();
            long stepWaitMs    = ConfigFactory.getWaitConfig().selectDocScrollStepWaitMs();
            long confirmWaitMs = ConfigFactory.getWaitConfig().selectDocScrollNoProgressConfirmWaitMs();
            int requiredStreak = ConfigFactory.getWaitConfig().selectDocScrollNoProgressStreakRequired();
            int noProgressStreak = 0;

            for (int attempt = 1; attempt <= safetyCap && checkbox == null; attempt++) {
                if (gridView != null) {
                    ActionUtils.scrollByWheel(gridView, deltaY);
                } else {
                    JavascriptExecutorUtils.resetGridScrollToTop();
                }
                Uninterruptibles.sleepUninterruptibly(stepWaitMs, TimeUnit.MILLISECONDS);

                Map<String, Object> curr = JavascriptExecutorUtils.getGridScrollProgress();
                long currScrollTop   = readLong(curr, "scrollTop", prevScrollTop);
                long currStoreCount  = readLong(curr, "storeCount", prevStoreCount);
                String currTopDoc    = readString(curr, "topDocNumber");
                String currBottomDoc = readString(curr, "bottomDocNumber");
                boolean atBottom     = readBool(curr, "atBottom", false);
                boolean storeLoading = readBool(curr, "storeLoading", false);

                try {
                    checkbox = driver.findElement(checkboxBy);
                    break;
                } catch (NoSuchElementException ex) {
                    // row not rendered yet — evaluate progress and continue
                }

                boolean progressed =
                        currScrollTop > prevScrollTop ||
                        currStoreCount > prevStoreCount ||
                        !Objects.equals(currTopDoc, prevTopDoc) ||
                        !Objects.equals(currBottomDoc, prevBottomDoc);

                if (atBottom && !storeLoading && !progressed) {
                    // Candidate end-of-list tick — don't trust a single quick read taken only
                    // stepWaitMs after the last one; an async BufferedStore page-fetch may still
                    // be landing. Wait the longer, independent confirmation window, re-attempt the
                    // direct find (the row may render during it), then re-read fresh scroll state
                    // before counting this as one genuinely confirmed no-progress signal.
                    Uninterruptibles.sleepUninterruptibly(confirmWaitMs, TimeUnit.MILLISECONDS);
                    try {
                        checkbox = driver.findElement(checkboxBy);
                    } catch (NoSuchElementException ex2) {
                        // still not present after the confirmation wait
                    }

                    if (checkbox == null) {
                        Map<String, Object> confirm = JavascriptExecutorUtils.getGridScrollProgress();
                        long confirmScrollTop    = readLong(confirm, "scrollTop", currScrollTop);
                        long confirmStoreCount   = readLong(confirm, "storeCount", currStoreCount);
                        String confirmTopDoc     = readString(confirm, "topDocNumber");
                        String confirmBottomDoc  = readString(confirm, "bottomDocNumber");
                        boolean confirmAtBottom  = readBool(confirm, "atBottom", atBottom);
                        boolean confirmLoading   = readBool(confirm, "storeLoading", storeLoading);

                        boolean stillNoProgress = confirmAtBottom && !confirmLoading
                                && confirmScrollTop == currScrollTop
                                && confirmStoreCount == currStoreCount
                                && Objects.equals(confirmTopDoc, currTopDoc)
                                && Objects.equals(confirmBottomDoc, currBottomDoc);

                        if (stillNoProgress) {
                            noProgressStreak++;
                            currScrollTop = confirmScrollTop;
                            currStoreCount = confirmStoreCount;
                            currTopDoc = confirmTopDoc;
                            currBottomDoc = confirmBottomDoc;
                            atBottom = confirmAtBottom;
                            storeLoading = confirmLoading;
                        } else {
                            noProgressStreak = 0;
                        }
                    }
                } else {
                    noProgressStreak = 0;
                }

                if (checkbox == null && noProgressStreak >= requiredStreak) {
                    long scrollHeight = readLong(curr, "scrollHeight", -1L);
                    Log.warn("{}VoucherMainGrid: End-of-list reached at attempt [{}] for [{}] — diagnostic "
                            + "snapshot: scrollTop=[{}] scrollHeight=[{}] clientHeight=[{}] storeCount=[{}] "
                            + "atBottom=[{}] storeLoading=[{}] confirmedNoProgressStreak=[{}] topDoc=[{}] "
                            + "bottomDoc=[{}]",
                            domain.name(), attempt, documentNumber, currScrollTop, scrollHeight, clientHeight,
                            currStoreCount, atBottom, storeLoading, noProgressStreak, currTopDoc, currBottomDoc);
                    break;
                }
                prevScrollTop  = currScrollTop;
                prevStoreCount = currStoreCount;
                prevTopDoc     = currTopDoc;
                prevBottomDoc  = currBottomDoc;
            }
        }

        // Strategy 3: ExtJS focusRow — last resort.
        if (checkbox == null) {
            long focusedIdx = JavascriptExecutorUtils.focusGridRowByDocument(documentNumber);
            if (focusedIdx >= 0) {
                Uninterruptibles.sleepUninterruptibly(
                        ConfigFactory.getWaitConfig().selectDocFocusRowRenderMs(), TimeUnit.MILLISECONDS);
                try {
                    checkbox = driver.findElement(checkboxBy);
                } catch (NoSuchElementException ex) {
                    // give up
                }
            }
        }

        return checkbox;
    }

    /**
     * Waits for visibility, then clicks the already-located checkbox — shared tail of both
     * {@link #selectDocumentInListPage(String)} and {@link #selectDocumentIfPresentOnCurrentPage(String)}.
     *
     * <p><b>Idempotent</b>: if the checkbox already reads as checked, this is a no-op — no click
     * fires at all. This matters because ExtJS check-columns <i>toggle</i> on click: clicking an
     * already-checked box would uncheck it. Making "select this document" safe to call more than
     * once (e.g. once before Revert, again before Actions→Edit, in case the grid reloaded and
     * cleared selection in between) requires never clicking a box that's already in the wanted
     * state.
     *
     * <p>Exactly ONE click is attempted — deliberately no blind retry-on-not-detected. A retry
     * would be safe only if the checked-state detection below were 100% certain; if it instead has
     * a false negative (the click actually worked but the detection missed it), a second click
     * would toggle a correctly-checked box back OFF, which is worse than just failing loudly on
     * the first attempt. Verifies afterward and fails fast with a clear diagnostic instead of
     * letting the caller proceed as if selection succeeded.
     */
    private void clickCheckbox(String documentNumber) {
        By checkboxBy = checkboxLocator(documentNumber);
        WebElement checkbox = WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsMinTimeout())
                .until(ExpectedConditions.visibilityOfElementLocated(checkboxBy));

        if (isChecked(checkbox)) {
            Log.debug("{}VoucherMainGrid: Checkbox for [{}] already checked — selection is idempotent, skipping click",
                    domain.name(), documentNumber);
            return;
        }

        Uninterruptibles.sleepUninterruptibly(
                ConfigFactory.getWaitConfig().selectDocClickBufferMs(), TimeUnit.MILLISECONDS);

        // Click via the By locator, not the WebElement resolved above, so a grid re-render between
        // the idempotency check and this click is recovered by re-resolving fresh on retry — the
        // previous catch-and-JS-click fallback reused that same (possibly now-stale) WebElement, so
        // it could never actually recover from a StaleElementReferenceException; clickWithStaleRetry
        // is the established fix for exactly this ExtJS re-render race (see CashBankPopup/
        // AllocateSitesPopup/GeneralVoucherExpenseIncomeDetail for the same idiom).
        SeleniumUtils.clickWithStaleRetry(checkboxBy, WaitType.CLICKABLE,
                domain.name() + "_VOUCHER_SELECT_CHECKBOX_" + documentNumber);
        JavascriptExecutorUtils.waitForExtJS();

        // Native UI, no ExtJS internal-state reads (per feedback_native_ui_over_extjs): the
        // checkbox column is confirmed to be an x-grid-checkcolumn — ExtJS's per-record
        // boolean-toggle column — and it's not proven that toggling it is the SAME interaction
        // that drives the grid's real row-selection model the Actions→Edit/Delete menu items
        // actually key off (confirmed live, run 20260901_170830, FIN-GV-EDIT-HEADER-03/
        // DELETE-01: the checkbox visibly registers as checked, yet Actions→Edit/Delete stayed
        // disabled through two full retry windows). DOUBLE_CLICK_EDIT — a native double-click
        // directly on the row — reliably opens Edit without ever depending on that enabled
        // state at all, the strongest evidence a plain checkbox click may not be it. Give the
        // row the same extra click a real user selecting it would naturally give: the
        // document-number cell already used to locate this row.
        SeleniumUtils.clickWithStaleRetry(rowAnchorLocator(documentNumber), WaitType.CLICKABLE,
                domain.name() + "_VOUCHER_SELECT_ROW_" + documentNumber);
        JavascriptExecutorUtils.waitForExtJS();

        if (!waitUntilChecked(checkboxBy)) {
            throw new IllegalStateException(domain.name() + "VoucherMainGrid: Checkbox for [" + documentNumber
                    + "] did not become checked after click — Actions→Edit/Revert would silently no-op "
                    + "from here. Diagnostics: " + JavascriptExecutorUtils.getGridDiagnostics());
        }
    }

    /**
     * {@code true} once the row is selected — checked via the row's own {@code aria-selected}
     * attribute (confirmed live: DevTools inspection of a checked row showed
     * {@code <tr class="x-grid-row" role="row" aria-selected="true">}, a standard ARIA
     * grid-selection attribute), reached the same way {@link #doubleClickRowToOpen} already
     * walks from the checkbox to its row (via {@code ./ancestor::tr[1]}). Replaces an earlier
     * JS-based check against ExtJS's {@code SelectionModel} — confirmed working, but a
     * xpath/attribute check is preferred here over JavaScript, and is no less reliable now that
     * {@code aria-selected} is a confirmed signal rather than a guess. The checkbox's own
     * {@code class} (checked-state suffix) and the row's {@code class} (selected-state suffix)
     * are kept as a secondary, still attribute-based fallback in case {@code aria-selected} is
     * ever absent for some reason — not because either is expected to fire.
     */
    private boolean isChecked(WebElement checkbox) {
        // Every DOM read below — not just the initial ancestor lookup — is wrapped in one
        // try/catch: an exact-match filter apply (or any other grid rebuild) can invalidate the
        // checkbox/row handles at any point between locating them and reading their attributes,
        // not only at the first lookup (confirmed live — run 20260901_151519's REVERT-01 failure
        // was a StaleElementReferenceException from the row-class read below, which previously had
        // no stale-guard). Treat any such failure as "not yet checked" so waitUntilChecked's poll
        // loop retries against a freshly-resolved element on its next iteration, instead of the
        // exception escaping the loop uncaught.
        //
        // Both signals below are confirmed present in real captured DOM. The two fallbacks this
        // method used to carry were NOT: `checkcolumn-checked` on the checkbox span and
        // `x-grid-row-selected` on the row each have ZERO occurrences across every captured
        // snapshot in this repo — the checkbox span is rendered bare
        // (`<span class="x-grid-checkcolumn">`) and selection is signalled only on its ancestors.
        // They were dead code giving false confidence, so they are replaced with the real
        // secondary signal: `x-grid-item-selected` on the row's own <table> wrapper (which appears
        // alongside aria-selected="true" in every confirmed-selected capture).
        try {
            WebElement row = checkbox.findElement(By.xpath("./ancestor::tr[1]"));
            String ariaSelected = row.getAttribute("aria-selected");
            if ("true".equals(ariaSelected)) {
                return true;
            }
            WebElement rowTable = checkbox.findElement(By.xpath("./ancestor::table[1]"));
            String rowTableClass = rowTable.getAttribute("class");
            return rowTableClass != null && rowTableClass.contains("x-grid-item-selected");
        } catch (Exception ignored) {
            return false; // element went stale mid-read — treat as not-yet-checked, caller keeps polling
        }
    }

    /** Polls {@link #isChecked(WebElement)} (short timeout, no threshold-widening) — re-resolves
     *  the checkbox fresh from {@code checkboxBy} on every iteration (rather than polling a single
     *  cached reference) so a grid re-render mid-poll can't surface as a stale-element read; a
     *  momentary "not rendered yet" is just treated as still-unchecked and polling continues. */
    private boolean waitUntilChecked(By checkboxBy) {
        WebDriver driver = DriverManager.getDriver();
        long deadline = System.currentTimeMillis() + CHECKBOX_STATE_CHECK_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (isChecked(driver.findElement(checkboxBy))) {
                    return true;
                }
            } catch (NoSuchElementException ignored) {
                // row not rendered at this instant — keep polling rather than failing outright
            }
            Uninterruptibles.sleepUninterruptibly(CHECKBOX_STATE_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
        return false;
    }

    // ── Tiny readers that coerce values out of the JS-returned progress Map — mirrors
    // SelectDocumentPopup's identical helpers (Selenium returns JS numbers as Long or Double
    // depending on the value, so both are normalised here). ──
    private static long readLong(Map<String, Object> m, String key, long fallback) {
        if (m == null) return fallback;
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    private static boolean readBool(Map<String, Object> m, String key, boolean fallback) {
        if (m == null) return fallback;
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static String readString(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
