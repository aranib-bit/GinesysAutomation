package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.pages.enums.Finance.Operations.Common.FooterMode;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;

import java.util.List;

/**
 * Common save/close footer shared by AP, AR, and future voucher types.
 * Subclasses pass their VoucherDomain to the constructor; log messages and
 * the close-action key are derived automatically from the domain.
 */
public abstract class VoucherFooterBase {

    private static final By CLOSE_BUTTON = By.xpath("//*[@class='mdi mdi-24px mdi-close']");

    /**
     * Window-scoped Save button — NOT the plain {@code //span[text()='ave']}
     * (unscoped: matches the FIRST such node anywhere in the DOM, in document order, regardless
     * of which window it belongs to). Confirmed live (reports/runs/20260913_170619 and
     * _173951, FIN-GV-ADD-COMBO-01): Save clicked clean (no overlay/interception logged), the
     * "Verify balanced before Save"/{@code aria-disabled} check reported enabled, yet the app
     * never responded at all for ~90s — the same unscoped-locator failure class already found
     * and fixed once in this codebase for {@code PaymentInformationPopup.OK_BUTTON} (see its
     * Javadoc), applied here on the same reasoning even without an equally direct DOM capture of
     * the wrong element this time. Scoped to the currently-open Add/Edit window via the same
     * {@code winTitleType1} title text {@code GeneralVoucherAdd#verifyOnThisPage()} already reads
     * through {@code PageHeaderUtils#getWindowTitleText()} — {@link VoucherDomain#getAddFormHeader()}/
     * {@link VoucherDomain#getEditFormHeader()} are the single source of truth for that text, not
     * a re-hardcoded string.
     */
    private static String windowScopeXpath(String windowTitle) {
        return "//div[contains(@class,'winTitleType1') and normalize-space()='" + windowTitle + "']"
                + "/ancestor::div[contains(@class,'x-window')][1]";
    }

    private final String voucherLabel;
    private final String closeKey;
    private final By saveButton;
    private final By saveButtonEnabled;
    private final String windowScopeUnion;

    protected VoucherFooterBase(VoucherDomain domain) {
        this.voucherLabel = domain.getVoucherLabel();
        this.closeKey     = domain.name() + "_VOUCHER_CLOSE";
        // Either the Add or the Edit window's title can be the one open — same "accept both"
        // shape as GeneralVoucherAdd#verifyOnThisPage(), combined via xpath's own "|" union
        // rather than two separate locators, so callers still get one By to wait/click on.
        String addScope  = windowScopeXpath(domain.getAddFormHeader());
        String editScope = windowScopeXpath(domain.getEditFormHeader());
        this.windowScopeUnion = addScope + " | " + editScope;
        this.saveButton = By.xpath("(" + addScope + " | " + editScope + ")//span[text()='ave']/ancestor::a[1]");
        this.saveButtonEnabled = By.xpath("(" + addScope + " | " + editScope
                + ")//span[text()='ave']/ancestor::a[1][@aria-disabled='false']");
    }

    /** Polls up to {@code timeoutSeconds} for ExtJS to report Save as enabled
     *  ({@code aria-disabled='false'}) — the Save button is an ExtJS anchor styled/greyed via
     *  {@code aria-disabled}, not the plain HTML {@code disabled} attribute, so {@code
     *  WebElement.isEnabled()}/{@code elementToBeClickable} does NOT reliably reflect whether
     *  ExtJS itself considers Save clickable (confirmed live: reports/runs/20260913_133337 shows
     *  Save fired while visibly greyed out, the voucher unbalanced). */
    public boolean waitUntilSaveEnabled(int timeoutSeconds) {
        return SeleniumUtils.isElementPresent(saveButtonEnabled, timeoutSeconds);
    }

    public DocumentPopup clickSave() {
        ExtentLogger.info("Clicking Save on " + voucherLabel + " form...");
        SeleniumUtils.click(saveButton, WaitType.CLICKABLE, "SAVEBUTTON");
        return new DocumentPopup();
    }

    /** Clicks the X to close the voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox clickClose() {
        SeleniumUtils.click(CLOSE_BUTTON, WaitType.CLICKABLE, closeKey);
        ExtentLogger.info("i Confirm dialog detected after [" + closeKey + "] — handled by page flow");
        return new DocumentMessageBox();
    }

    /** UI-contract validation for the Save button — text, style, and expected enabled/disabled
     *  state. Placed here (not per-domain) so AR/AP/GV all inherit it, same reasoning as
     *  {@code VoucherDocumentInformationBase}'s own {@code validate*Ui} methods. */
    public List<UiValidationFailure> validateSaveButtonUi(UiValidationLevel level, boolean expectedEnabled) {
        return UiComponentValidator.validateButton(saveButton, "SAVE_BUTTON", "Save", expectedEnabled, level);
    }

    /** Window-scoped light footer button (Copy, Close — class {@code gsl_footerLightBtn}, confirmed
     *  from the live DOM; Save is {@code gsl_footerDarkBtn}) located by its full visible text.
     *  {@code normalize-space(.)} on the anchor reads "Close" even though ExtJS splits the mnemonic
     *  letter into {@code <u>C</u>lose} (the same reason Save's own locator matches
     *  {@code text()='ave'}). */
    private By footerButton(String text) {
        return By.xpath("(" + windowScopeUnion + ")//a[contains(@class,'gsl_footerLightBtn') and normalize-space(.)='"
                + text + "']");
    }

    /** Page-footer buttons — Copy, Save, Close — per {@link FooterMode}: a button the mode expects
     *  is checked for text, enabled state and style; a button it does not expect (Copy on Edit,
     *  Copy and Save on a Posted document) is checked as absent by design. */
    public List<UiValidationFailure> validateFooterButtonsUi(UiValidationLevel level, FooterMode mode) {
        List<UiValidationFailure> failures = new java.util.ArrayList<>();
        if (mode.copyExpected()) {
            failures.addAll(UiComponentValidator.validateButton(footerButton("Copy"), "FOOTER_BUTTON_COPY", "Copy", true,
                    com.gsl.utils.ui.FieldStyleSpec.FOOTER_LIGHT_BUTTON, level));
        } else {
            failures.addAll(UiComponentValidator.validateAbsentByDesign(footerButton("Copy"),
                    "FOOTER_BUTTON_COPY", "Copy is shown on Add only"));
        }
        if (mode.saveExpected()) {
            failures.addAll(validateSaveButtonUi(level, true));
        } else {
            failures.addAll(UiComponentValidator.validateAbsentByDesign(saveButton,
                    "SAVE_BUTTON", "Save is shown only for an Unposted document"));
        }
        failures.addAll(UiComponentValidator.validateButton(footerButton("Close"), "FOOTER_BUTTON_CLOSE", "Close", true,
                com.gsl.utils.ui.FieldStyleSpec.FOOTER_LIGHT_BUTTON, level));
        return failures;
    }
}
