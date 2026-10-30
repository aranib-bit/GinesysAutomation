package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.SubLedgerRowIdentity;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerFillRow;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import java.util.List;

/**
 * Common XPath templates and button-click logic shared by AR and AP Voucher
 * sub-ledger page components.
 *
 * XPaths identical across both voucher types are declared as static final constants.
 * The only difference is the Sub Ledger column header text (AR: "Sub Ledger",
 * AP: "Sub-Ledger"), which is resolved from VoucherDomain — the same pattern as
 * resolveDocumentDate(VoucherDomain) in VoucherDocumentInformationBase.
 */
public abstract class VoucherSubLedgerInformationBase implements AdjustmentEntryParent {

    // ── Per-domain fields — resolved once in constructor ──────────────────────

    private final VoucherDomain domain;
    private final String colSubLedger;
    private final String colRefSite;
    private final String colSlId;
    /** False when {@code domain}'s Sub-Ledger Information grid has no "Sub Ledger Id" column
     *  (e.g. AR — see {@link VoucherDomain#excludesCommonColumn}) — the WITH_SLID templates
     *  below would then never match a real row, so callers must fall back to name+refSite. */
    private final boolean slIdColumnAvailable;

    // ── XPath templates — built once in constructor ───────────────────────────

    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE;
    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID;
    private final String PAYMENT_BUTTON_TEMPLATE;
    private final String PAYMENT_BUTTON_TEMPLATE_WITH_SLID;

    protected VoucherSubLedgerInformationBase(VoucherDomain domain) {
        this.domain = domain;
        this.colSubLedger = domain.getSubLedgerColumnHeader().columnIdXPath();
        this.colRefSite   = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(domain).columnIdXPath();
        this.colSlId      = CommonVoucherGridColumnHeader.SUB_LEDGER_ID.resolveFor(domain).columnIdXPath();
        this.slIdColumnAvailable = !domain.excludesCommonColumn(CommonVoucherGridColumnHeader.SUB_LEDGER_ID);

        SUBLEDGERADJUSTBUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.ADJUST.label() + "']";

        SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + colSlId + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.ADJUST.label() + "']";

        PAYMENT_BUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']";

        PAYMENT_BUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + colSlId + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']";
    }

    // ── Protected methods — inherited by AR and AP subclasses ─────────────────

    @Override
    public VoucherDomain voucherDomain() {
        return domain;
    }

    /**
     * A grid cell editor can start on its own after a header dropdown closes (the grid regains focus and its cellediting plugin opens the focused
     * cell). If it is still open when the Adjustment Entry window appears, the editor's focus raises the Edit window ABOVE the modal Adjustment
     * Entry, and the next click is intercepted by the Edit window's toolbar (FIN-AP-EDIT-HEADER-02: Edit window z=29000, Adjustment Entry z=19000,
     * an empty combo editor active). Cancelling the empty editor with Escape - what a user would do - before the Adjust click prevents that.
     */
    private void cancelStrayCellEditor() {
        By activeEditor = By.xpath("//div[(contains(@class,'x-editor') or contains(@class,'x-grid-editor')) and not(contains(@class,'x-hide-display'))]");
        for (int attempt = 1; attempt <= 3; attempt++) {
            JavascriptExecutorUtils.waitForExtJS();
            boolean open = DriverManager.getDriver().findElements(activeEditor).stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed);
            if (!open) return;
            Log.warn("[SL_ADJUST] A grid cell editor is open before the Adjust click (attempt {}) - cancelling it with Escape.", attempt);
            DriverManager.getDriver().switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE);
        }
    }

    protected void clickAdjust(String subLedgerName, String referenceSite, String slid) {
        cancelStrayCellEditor();
        String xpath = (slIdColumnAvailable && slid != null && !slid.isBlank())
                ? String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID, subLedgerName, referenceSite, slid)
                : String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE, subLedgerName, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "SUBLEDGERADJUSTBUTTON");
    }

    /**
     * Reads row {@code rowNumber}'s (1-based) own Sub-Ledger name/Reference Site/Sub Ledger Id
     * text straight off the rendered grid — the inverse of {@link #clickAdjust}'s own
     * {@code data-columnid}-scoped locating shape: that method LOCATES a row given these three
     * values; this method READS them, for a caller that only knows the row's position (e.g.
     * re-tagging an already-populated Edit-mode row, which has no test-data-supplied identity to
     * locate it by). Safe to call regardless of the row's current tag state — Sub-Ledger name/
     * Reference Site/Sub Ledger Id are a persistent part of the document (finvchdet/finsl),
     * unaffected by whether the row is currently tagged.
     */
    protected SubLedgerRowIdentity readRowIdentity(int rowNumber) {
        String subLedgerName = SeleniumUtils.getAttribute(
                By.xpath("(//tr[td[@data-columnid=" + colSubLedger + "]])[" + rowNumber
                        + "]//td[@data-columnid=" + colSubLedger + "]/div"),
                "textContent", "SUBLEDGER_NAME_ROW" + rowNumber);
        String referenceSite = SeleniumUtils.getAttribute(
                By.xpath("(//tr[td[@data-columnid=" + colRefSite + "]])[" + rowNumber
                        + "]//td[@data-columnid=" + colRefSite + "]/div"),
                "textContent", "REFERENCE_SITE_ROW" + rowNumber);
        String slId = slIdColumnAvailable
                ? SeleniumUtils.getAttribute(
                        By.xpath("(//tr[td[@data-columnid=" + colSlId + "]])[" + rowNumber
                                + "]//td[@data-columnid=" + colSlId + "]/div"),
                        "textContent", "SL_ID_ROW" + rowNumber)
                : null;
        return new SubLedgerRowIdentity(
                subLedgerName == null ? null : subLedgerName.trim(),
                referenceSite == null ? null : referenceSite.trim(),
                slId == null ? null : slId.trim());
    }

    /**
     * Report label identifying grid row {@code rowNumber} (1-based) uniquely by its business keys —
     * {@code Sub Ledger: <name> · Reference Site: <site>} (Reference Site omitted where the voucher has no
     * such column, e.g. AR; Sub Ledger Id appended when shown) — so a report row is never just "Row N".
     * A row that cannot be read yet (blank / not rendered) reads {@code "not yet filled"}.
     */
    public String rowIdentityLabel(int rowNumber) {
        try {
            SubLedgerRowIdentity id = readRowIdentity(rowNumber);
            StringBuilder sb = new StringBuilder();
            if (id.getSubLedgerName() != null && !id.getSubLedgerName().isBlank()) {
                sb.append("Sub Ledger: ").append(id.getSubLedgerName());
            }
            if (id.getReferenceSite() != null && !id.getReferenceSite().isBlank()) {
                sb.append(sb.length() > 0 ? " · " : "").append("Reference Site: ").append(id.getReferenceSite());
            }
            if (id.getSlId() != null && !id.getSlId().isBlank()) {
                sb.append(sb.length() > 0 ? " · " : "").append("Sub Ledger Id: ").append(id.getSlId());
            }
            return sb.length() == 0 ? "not yet filled" : sb.toString();
        } catch (RuntimeException e) {
            return "not yet filled";
        }
    }

    protected PaymentInformationPopup clickPaymentButton(String subLedger, String referenceSite, String slid) {
        String xpath = (slIdColumnAvailable && slid != null && !slid.isBlank())
                ? String.format(PAYMENT_BUTTON_TEMPLATE_WITH_SLID, subLedger, referenceSite, slid)
                : String.format(PAYMENT_BUTTON_TEMPLATE, subLedger, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "PAYMENT_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
        return createPaymentPopup();
    }

    /**
     * Clicks the native Add Row button (mdi-playlist-plus icon) in the sub-ledger grid.
     * Replaces the JS {@code store.add({})} injection.
     */
    protected void clickAddRowButton() {
        SeleniumUtils.click(
                By.xpath("//i[contains(@class,'mdi-playlist-plus')]" +
                         "/parent::span[contains(@class,'x-btn-inner-IconButton-small')]"),
                WaitType.CLICKABLE, "ADD_SUBLEDGER_ROW");
    }

    /** The ExtJS {@code itemId} of this voucher's Sub-Ledger Information grid (AP {@code apvch_grid}, AR {@code arvch_grid}); General Voucher's grid is not itemId-addressed, so it does not override this. */
    protected String gridItemId() {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no itemId-addressed Sub-Ledger grid");
    }

    // ── Row primitives shared by AP and AR (one copy; only the grid id differs) ───────────────

    /**
     * NATIVE sub-ledger selection: open the real inline grid combo, type the sub-ledger name, and click the matching picker row - exactly
     * as a user would, so the application runs its own combo {@code select} handler (GL evaluation and, on AP, TDS applicability).
     * Pages forward through the picker's own Next-Page control when the exact match is not on the rendered page.
     */
    protected void setSubLedgerNameNative(String subledgerName, String id, String referenceSite, int rowIndex) {
        String inputId = JavascriptExecutorUtils.openSubLedgerComboEditor(DriverManager.getDriver(), rowIndex, gridItemId());
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_NATIVE] Could not open Sub-Ledger combo editor on row " + rowIndex + " for '" + subledgerName + "'");
        }

        // UI validation (read only): what the Sub-Ledger picker shows before and after typing.
        com.gsl.validation.ui.PickerUi.Snapshot pickerBefore = capturingPickers() ? com.gsl.validation.ui.PickerUi.snapshot() : null;
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_COMBO_INPUT");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, subledgerName, "SL_COMBO_INPUT");
        JavascriptExecutorUtils.waitForExtJS();

        By option = subLedgerPickerRow(subledgerName, id);
        // The filtered list is read once the fill has found the option (it renders after a debounce, not right after typing).
        final com.gsl.validation.ui.PickerUi.Snapshot[] pickerAfter = {com.gsl.validation.ui.PickerUi.Snapshot.none()};
        SeleniumUtils.clickBoundlistOptionAcrossPages(
                "//div[contains(@class,'x-boundlist-floating')]", option, "SL_OPTION_" + subledgerName,
                () -> { if (capturingPickers()) pickerAfter[0] = com.gsl.validation.ui.PickerUi.snapshot(); });
        if (capturingPickers()) {
            recordPicker(rowIndex, new com.gsl.validation.ui.PickerUi.Observation("Sub Ledger picker",
                    subLedgerPickerColumns(), subledgerName, subledgerName, id == null || id.isBlank() ? null : id,
                    java.util.Map.of(), pickerBefore, pickerAfter[0]));
        }
        ReportLogger.fieldEntry(subledgerName + " selected from dropdown");
        // Let the application's combo select handler run its GL (+ TDS-applicability) evaluation.
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForComponentReady();

        Log.debug("[SL_NATIVE] Selected SL='{}' (id='{}') on row {}", subledgerName, id, rowIndex);
        ReportLogger.trace("[SL_NATIVE] row=" + rowIndex + " selected SL='" + subledgerName
                + "' id='" + (id == null || id.isEmpty() ? "—" : id) + "'");

        if (referenceSite != null && !referenceSite.trim().isEmpty()) {
            setSubLedgerReferenceSite(referenceSite, rowIndex);
        }
    }

    /**
     * Types {@code subledgerName} into the first row's Sub Ledger picker and reports whether the picker lists it - WITHOUT selecting
     * anything (an extinct vendor must never be offered). Judged only after {@code picker.search.settle.ms}, because the filtered list
     * renders after a debounce.
     */
    public boolean isSubLedgerOfferedByPicker(String subledgerName, String id) {
        String inputId = JavascriptExecutorUtils.openSubLedgerComboEditor(DriverManager.getDriver(), 0, gridItemId());
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException("[SL_PICKER] Could not open the Sub-Ledger combo editor on row 0 for '" + subledgerName + "'");
        }
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_COMBO_INPUT");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, subledgerName, "SL_COMBO_INPUT");
        JavascriptExecutorUtils.waitForExtJS();
        By option = subLedgerPickerRow(subledgerName, id == null ? "" : id);
        long deadline = System.currentTimeMillis() + ConfigFactory.getWaitConfig().pickerSearchSettleMs();
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            if (!DriverManager.getDriver().findElements(option).isEmpty()) {
                found = true;
                break;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ReportLogger.business("[SL_PICKER] '" + subledgerName + "' is " + (found ? "OFFERED" : "not offered") + " by the Sub Ledger picker.");
        DriverManager.getDriver().switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE);
        return found;
    }

    /**
     * Locator for a row of the Sub-Ledger picker (an ExtJS boundlist; each cell carries a clean {@code data-qtip}, so matching on it is
     * highlight-proof). With an {@code id}, a row must hold BOTH the exact Name and Id (names are not unique); otherwise Name only.
     */
    protected By subLedgerPickerRow(String name, String id) {
        String n = name.trim();
        String base = "//div[contains(@class,'x-boundlist-floating')]"
                + "//div[contains(@class,'x-boundlist-item')";
        if (id != null && !id.trim().isEmpty()) {
            return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]"
                                 + " and .//td[@data-qtip=\"" + id.trim() + "\"]]");
        }
        return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]]");
    }

    protected void addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver(), gridItemId());
    }

    protected void setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        if (referenceSite == null || referenceSite.trim().isEmpty()) return;
        final String tag = "[" + domain.name() + " SL RefSite]";
        // The app asynchronously fills Reference Site after SL selection; poll before deciding skip vs. native path.
        String existing = waitForReferenceSiteAutoPopulation(rowIndex);
        if (existing != null && !existing.trim().isEmpty()) {
            ExtentLogger.info(tag + " Row " + (rowIndex + 1) + ": already '" + existing + "' — not changing (skip).");
            return;
        }
        String inputId = JavascriptExecutorUtils.openReferenceSiteCellEditor(DriverManager.getDriver(), rowIndex, gridItemId());
        if (inputId == null || inputId.isEmpty()) {
            ExtentLogger.warning(tag + " Row " + (rowIndex + 1)
                    + ": native editor did not open — using JS injection fallback for '" + referenceSite + "'");
            Log.warn("[SL_REFSITE_NATIVE] {} row {} — falling back to JS injection for '{}'", domain.name(), rowIndex + 1, referenceSite);
            JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite, gridItemId());
            return;
        }
        ExtentLogger.info(tag + " Row " + (rowIndex + 1) + ": native path — typing '" + referenceSite + "'");
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_REFSITE");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, referenceSite, "SL_REFSITE");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath("//li[@role='option'][normalize-space(.)='" + referenceSite + "']");
        WaitUtils.fluentWaitBuilder().until(driver -> {
            driver.findElement(option).click();
            return true;
        });
        ReportLogger.fieldEntry(referenceSite + " selected from dropdown");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        ExtentLogger.info(tag + " Row " + (rowIndex + 1) + ": selected '" + referenceSite + "' via native");
    }

    /** Waits up to {@code shortWaitSeconds} for the app to auto-populate the Reference Site cell; returns it, or {@code ""} on timeout. */
    private String waitForReferenceSiteAutoPopulation(int rowIndex) {
        long timeoutSeconds = ConfigFactory.getWaitConfig().shortWaitSeconds();
        try {
            return WaitUtils.webDriverWait(timeoutSeconds).until(driver -> {
                String val = JavascriptExecutorUtils.getSubLedgerReferenceSiteName(driver, rowIndex, gridItemId());
                return (val != null && !val.trim().isEmpty()) ? val : null;
            });
        } catch (TimeoutException e) {
            ExtentLogger.info("[" + domain.name() + " SL RefSite] Row " + (rowIndex + 1)
                    + ": auto-population did not occur within " + timeoutSeconds + "s — will use native path.");
            return "";
        }
    }

    protected String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex, gridItemId());
    }

    protected void setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        String inputId = JavascriptExecutorUtils.openAmountCellEditor(DriverManager.getDriver(), rowIndex, gridItemId());
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException("[SL_AMOUNT_NATIVE] Could not open Amount editor on row " + rowIndex
                    + " for " + domain.name() + " grid [" + gridItemId() + "]");
        }
        By amountInput = By.id(inputId);
        SeleniumUtils.clear(amountInput, "SL_AMOUNT");
        SeleniumUtils.sendKeys(amountInput, WaitType.VISIBLE, subLedgerAmount, "SL_AMOUNT");
        // Tab commits the value to the ExtJS store record - without it the typed amount is never written ("Amount is required" on Save).
        SeleniumUtils.pressTab(amountInput, "SL_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Pass 2 of the sub-ledger fill: re-apply the Reference Site only for rows that opted in ({@code changeReferenceSite}), the row-0 retry
     * for the same reason, then read back what the app actually populated into every row.
     */
    protected void applyReferenceSitePass(List<? extends VoucherSubLedgerFillRow> sls) {
        final String tag = "[" + domain.name() + " SL RefSite]";
        for (int i = 0; i < sls.size(); i++) {
            final int idx = i;
            final VoucherSubLedgerFillRow sl = sls.get(i);
            final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite() : "";
            final String rowKey = SubLedgerRowStepRegistry.key(sl.getLineNo(), idx);
            if (sl.isChangeReferenceSite()) {
                SubLedgerRowStepRegistry.stepUnderRow(rowKey,
                        "Change Reference Site — " + (refSite.isEmpty() ? "(none)" : refSite),
                        () -> setSubLedgerReferenceSite(refSite, idx));
            } else {
                ExtentLogger.info(tag + " Row " + (idx + 1) + ": skipping Pass-2 set (changeReferenceSite=false) — current value kept");
            }
        }
        // Row-0 retry - only if that row opted in
        final VoucherSubLedgerFillRow sl0 = sls.get(0);
        if (sl0.isChangeReferenceSite()) {
            final String refSite = sl0.getReferenceSite() != null ? sl0.getReferenceSite() : "";
            SubLedgerRowStepRegistry.stepUnderRow(SubLedgerRowStepRegistry.key(sl0.getLineNo(), 0),
                    "Change Reference Site (row-0 retry) — " + (refSite.isEmpty() ? "(none)" : refSite),
                    () -> setSubLedgerReferenceSite(refSite, 0));
        }
        // Read back actual values regardless - captures what the app populated
        for (int i = 0; i < sls.size(); i++) {
            String actual = getSubLedgerReferenceSite(i);
            if (actual != null && !actual.isEmpty()) sls.get(i).setReferenceSite(actual);
        }
    }

    /** Opens each row's Cheque/RTGS popup and submits the payment that shares its line number (CASH rows are skipped). */
    protected void handlePaymentInformation(List<? extends VoucherSubLedgerFillRow> subLedgerList, List<? extends VoucherPaymentInfo> paymentList) {
        for (VoucherSubLedgerFillRow subLedger : subLedgerList) {
            final String slKey = (subLedger.getId() != null && !subLedger.getId().isBlank())
                    ? subLedger.getId() : subLedger.getSubLedger();
            com.gsl.reports.ActualResultsHolder.get().getUiPaymentInfoPopupMap().put(slKey, false);

            final String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            final VoucherPaymentInfo payment = paymentList.stream()
                    .filter(p -> p.getLineNo() != null
                            && p.getLineNo().equalsIgnoreCase(lineNo)
                            && (subLedger.getTestCaseID() == null || p.getTestCaseID() == null
                                || p.getTestCaseID().equalsIgnoreCase(subLedger.getTestCaseID())))
                    .findFirst()
                    .orElse(null);
            if (payment == null) continue;

            final PaymentSelectionType effectiveType = subLedger.getPaymentSelection() != null
                    ? subLedger.getPaymentSelection()
                    : payment.getPaymentMode();
            if (effectiveType == null || effectiveType == PaymentSelectionType.CASH) continue;

            final String refSite = subLedger.getReferenceSite() != null ? subLedger.getReferenceSite() : "";
            final String slid    = subLedger.getId() != null ? subLedger.getId() : "";
            SubLedgerRowStepRegistry.stepUnderRow(lineNo, "Payment — " + effectiveType.label(), () -> {
                final int rowIdx = subLedgerList.indexOf(subLedger);
                final PaymentInformationPopup popup = clickPaymentButton(subLedger.getSubLedger(), refSite, slid);
                notifyFill(l -> l.onPaymentOpened(rowIdx, popup));
                popup.onBeforeSubmit(() -> notifyFill(l -> l.onPaymentFilled(rowIdx, popup, payment)));
                popup.fillAndSubmit(payment);
                JavascriptExecutorUtils.waitForExtJS();
                com.gsl.reports.ActualResultsHolder.get().getUiPaymentInfoPopupMap().put(slKey, true);
            });
        }
    }

    protected abstract PaymentInformationPopup createPaymentPopup();

    // ── UI observer of the business fill (see SubLedgerFillListener) ─────────────────────────

    private com.gsl.validation.ui.voucher.SubLedgerFillListener fillUiListener;

    /** Installs (or, with {@code null}, removes) the observer the fill code announces its progress to. */
    public void setSubLedgerFillListener(com.gsl.validation.ui.voucher.SubLedgerFillListener listener) {
        this.fillUiListener = listener;
    }

    /** Calls the observer if one is installed; a failure inside it is logged and never breaks the fill. */
    protected final void notifyFill(java.util.function.Consumer<com.gsl.validation.ui.voucher.SubLedgerFillListener> call) {
        if (fillUiListener == null) return;
        try {
            call.accept(fillUiListener);
        } catch (RuntimeException e) {
            com.gsl.utils.Log.warn("[SubLedgerFillListener] UI validation hook skipped: {}", e.toString());
        }
    }

    // ── Picker (floating grid) snapshots for UI validation ─────────────────────────────────────

    private final java.util.Map<Integer, java.util.List<com.gsl.validation.ui.PickerUi.Observation>> pickerObservations =
            new java.util.HashMap<>();

    /** True while a UI observer is installed - snapshots are taken only then (no cost for plain business runs). */
    protected boolean capturingPickers() {
        return fillUiListener != null;
    }

    /** Stores what one picker showed before and after typing, for the row's UI checks. */
    protected final void recordPicker(int rowIndex, com.gsl.validation.ui.PickerUi.Observation observation) {
        pickerObservations.computeIfAbsent(rowIndex, k -> new java.util.ArrayList<>()).add(observation);
    }

    /** Returns and forgets the picker observations of a row (taken by the UI recorder once the row is filled). */
    public java.util.List<com.gsl.validation.ui.PickerUi.Observation> drainPickerObservations(int rowIndex) {
        java.util.List<com.gsl.validation.ui.PickerUi.Observation> out = pickerObservations.remove(rowIndex);
        return out == null ? java.util.List.of() : out;
    }

    /** Header texts of this voucher's Sub-Ledger picker, in order (confirmed per voucher from live screenshots). */
    protected java.util.List<String> subLedgerPickerColumns() {
        return java.util.List.of("Name", "Id", "Class Name");
    }

    // ── UI-contract validation — grid header ─────────────────────────────────────────────────
    //
    // Placed here (not per-domain) so AR/AP/GV all inherit it for the columns common to every
    // voucher's sub-ledger grid — same "base-class placement delivers free AR/AP reuse" reasoning
    // as VoucherDocumentInformationBase's own validate*Ui methods.

    /** Validates the Sub Ledger column header's text+style (resolved per-domain, e.g. AR/GV "Sub
     *  Ledger" vs. AP "Sub-Ledger"). */
    public List<UiValidationFailure> validateSubLedgerGridHeaderUi(UiValidationLevel level) {
        IVoucherGridColumnHeader col = domain.getSubLedgerColumnHeader();
        return UiComponentValidator.validateGridHeader(col.headerLocator(), "GRID_HEADER_SUB_LEDGER",
                col.headerText(), level);
    }

    /** Validates the Reference Site column header's text+style — {@code CONTRA}/AR-excluded
     *  domains should not call this (AR has no Reference Site column; see
     *  {@link VoucherDomain#excludesCommonColumn}). */
    public List<UiValidationFailure> validateReferenceSiteGridHeaderUi(UiValidationLevel level) {
        IVoucherGridColumnHeader col = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(domain);
        return UiComponentValidator.validateGridHeader(col.headerLocator(), "GRID_HEADER_REFERENCE_SITE",
                col.headerText(), level);
    }
}
