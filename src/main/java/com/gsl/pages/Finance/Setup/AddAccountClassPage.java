package com.gsl.pages.Finance.Setup;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.Finance.Setup.AddAccountClassFieldLabel;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The "Add Account Class" modal popup — a distinct page object from {@link AccountClassListPage}
 * since it is a genuinely separate UI surface with its own lifecycle (open → fill → Save → Close,
 * confirmed via the "Discard changes?" prompt (Yes) even after a successful Save — the shared
 * {@link DocumentMessageBox} handles that confirmation). The popup does NOT stay open/reset after
 * Save; callers must close it before the list's Add button or grid rows are usable again.
 *
 * Class Name locator confirmed against the live DOM. The remaining locators (Class Type trigger,
 * boundlist, Save/Close buttons, error dialog) are still best-guess — no existing popup-form page
 * object in this codebase has a fixed ExtJS combobox + text field + Save/Close pair to copy from
 * ({@code AccountGroupListPage} is a tree with an inline cell editor, not a modal). Confirm the
 * rest against the real DOM on the first live run.
 */
public class AddAccountClassPage {

    private static final class Loc {
        // Confirmed against the live DOM: the label is a <span>, not a <label>, and the match
        // must include the trailing colon.
        static final By CLASS_NAME_INPUT =
                By.xpath("//span[contains(text(),'" + AddAccountClassFieldLabel.CLASS_NAME.text() + "')]" +
                        "/ancestor::div[contains(@class,'x-form-item')]//input");

        // x-form-trigger matches the trigger-WRAP container, not the clickable arrow icon itself —
        // clicking it is a valid DOM click but doesn't fire ExtJS's expand handler. Every other
        // working combo-trigger locator in this codebase (ItemAddOthers, ItemAddGeneral,
        // PaymentInformationPopup, etc.) targets x-form-arrow-trigger specifically.
        static final By CLASS_TYPE_TRIGGER =
                By.xpath("//span[contains(text(),'" + AddAccountClassFieldLabel.CLASS_TYPE.text() + "')]" +
                        "/ancestor::div[contains(@class,'x-form-item')]//div[contains(@class,'x-form-arrow-trigger')]");

        // The combo's own display input (not the trigger arrow, not the floating boundlist) —
        // scoped to the "Class Type:" label so the read-back value is tied to this field
        // specifically, regardless of which dynamically-id'd boundlist was last open.
        static final By CLASS_TYPE_DISPLAY =
                By.xpath("//span[contains(text(),'" + AddAccountClassFieldLabel.CLASS_TYPE.text() + "')]" +
                        "/ancestor::div[contains(@class,'x-form-item')]//input");

        // ExtJS only ever has one live floating boundlist open at a time — safe to use unscoped,
        // same reasoning AccountGroupListPage's CELL_EDITOR_INPUT documents for the cell editor.
        private static final String BOUNDLIST_XPATH =
                "//div[contains(@class,'x-boundlist-floating') and not(contains(@class,'x-boundlist-hidden'))]";

        static final By BOUNDLIST_FLOATING = By.xpath(BOUNDLIST_XPATH);
        static final By BOUNDLIST_ITEMS    = By.xpath(BOUNDLIST_XPATH + "//td[@data-qtip]");

        static By boundlistItem(String label) {
            // data-qtip match, NOT normalize-space() — a search-highlight span inside the option
            // <td> breaks plain text matching (see subLedgerPickerRow's documented convention).
            return By.xpath(BOUNDLIST_XPATH + "//td[@data-qtip='" + label + "']");
        }

        // ExtJS button spans are sometimes truncated mid-word by an overlapping icon — the same
        // quirk VoucherFooterBase/CashBankPopup already document ('ave' for Save, 'K' for OK,
        // 'ancel' for Cancel). Matching on the tail substring 'ave' avoids depending on whichever
        // character(s) the icon happens to obscure.
        static final By SAVE_BUTTON = By.xpath("//span[contains(text(),'ave')]");

        // Confirmed live (2026-07-13 run): this popup does NOT use the mdi-close icon-font class
        // that other GINESYS forms use (DocumentMessageBox, VoucherFooterBase, GRC Close) — a
        // full-page DOM capture taken during the failure had zero mdi-close elements anywhere on
        // the page. The same session's ExtJS "Discard changes?" window (same title-bar chrome)
        // instead used the native ExtJS window-tool close button, class x-tool-close on the inner
        // tool-el — that's what this targets now.
        static final By CLOSE_BUTTON = By.xpath("//div[contains(@class,'x-tool-close')]");

        // Confirmed live: the server-side rejection dialog (e.g. duplicate/blank name) on this
        // popup dismisses via a "Close" button, not "OK" — standard ExtJS button markup, an
        // <a role="button"> wrapping the text span rather than a bare span.
        static final By SAVE_ERROR_OK =
                By.xpath("//a[@role='button'][.//span[@data-ref='btnInnerEl' and normalize-space(.)='Close']]");
    }

    public AddAccountClassPage typeClassName(String name) {
        WebElement input = SeleniumUtils.isElementPresent(Loc.CLASS_NAME_INPUT, 5)
                ? DriverManager.getDriver().findElement(Loc.CLASS_NAME_INPUT)
                : null;
        if (input == null) {
            throw new IllegalStateException("Class Name input not found on Add Account Class popup.");
        }

        // End -> Shift+Home -> type: reliable select-all-and-replace on a plain single-line input,
        // same technique AccountGroupListPage.typeGroupName uses (Ctrl+A proved unreliable there;
        // .clear() fires an unwanted blur/change event on some ExtJS-managed inputs).
        new Actions(DriverManager.getDriver())
                .sendKeys(input, Keys.END)
                .keyDown(Keys.SHIFT).sendKeys(Keys.HOME).keyUp(Keys.SHIFT)
                .sendKeys(name)
                .perform();

        String actualValue = input.getAttribute("value");
        if (!name.equals(actualValue)) {
            throw new IllegalStateException(
                    "Class Name value after typing was [" + actualValue + "], expected [" + name + "].");
        }
        return this;
    }

    /**
     * Opens the Class Type dropdown — skipped entirely if the field already shows a value, since
     * clicking the arrow is only needed to change what's currently displayed. If a click is
     * needed, clicks the trigger, and if the floating boundlist doesn't appear, clicks it again
     * before giving up.
     */
    public AddAccountClassPage openClassTypeDropdown() {
        String current = getSelectedClassType();
        if (current != null && !current.isBlank()) {
            return this; // already has a value — no need to open the boundlist
        }
        SeleniumUtils.click(Loc.CLASS_TYPE_TRIGGER, WaitType.CLICKABLE, "ACCOUNTCLASS_TYPE_TRIGGER");
        if (!SeleniumUtils.isElementPresent(Loc.BOUNDLIST_FLOATING, 3)) {
            // 2nd+ time in a session: the trigger click alone doesn't open it — click again.
            SeleniumUtils.click(Loc.CLASS_TYPE_TRIGGER, WaitType.CLICKABLE, "ACCOUNTCLASS_TYPE_TRIGGER_RETRY");
        }
        if (!SeleniumUtils.isElementPresent(Loc.BOUNDLIST_FLOATING, 5)) {
            throw new IllegalStateException(
                    "Class Type dropdown did not open after two trigger clicks.");
        }
        return this;
    }

    public AddAccountClassPage selectClassType(String label) {
        String current = getSelectedClassType();
        if (label.equalsIgnoreCase(current)) {
            return this; // already showing the desired value — nothing to select
        }

        openClassTypeDropdown();
        SeleniumUtils.click(Loc.boundlistItem(label), WaitType.CLICKABLE, "ACCOUNTCLASS_TYPE_OPTION[" + label + "]");
        WaitUtils.untilInvisible(Loc.BOUNDLIST_FLOATING);

        String actual = getSelectedClassType();
        if (!label.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Class Type after selection was [" + actual + "], expected [" + label + "].");
        }
        return this;
    }

    /**
     * Reads the Class Type field's own display value — not the floating boundlist — so the
     * result is positively tied to this field regardless of which boundlist was last open or
     * what dynamic id ExtJS assigned it.
     */
    public String getSelectedClassType() {
        return SeleniumUtils.getAttribute(Loc.CLASS_TYPE_DISPLAY, "value", "CLASS_TYPE_CURRENT");
    }

    /** Returns every visible Class Type option label — used by the fixed-dropdown-values check. */
    public List<String> getClassTypeOptions() {
        openClassTypeDropdown();
        List<String> options = DriverManager.getDriver().findElements(Loc.BOUNDLIST_ITEMS).stream()
                .map(e -> e.getAttribute("data-qtip"))
                .collect(Collectors.toList());
        new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
        WaitUtils.untilInvisible(Loc.BOUNDLIST_FLOATING);
        return options;
    }

    /** Clicks Save. The popup remains open afterward — call {@link #clickClose()} to dismiss it. */
    public AddAccountClassPage clickSave() {
        SeleniumUtils.click(Loc.SAVE_BUTTON, WaitType.CLICKABLE, "ACCOUNTCLASS_SAVE");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");
        return this;
    }

    /**
     * Dismisses a server-side "Cannot Save" style error dialog if one appeared after Save —
     * a no-op when Save succeeded or was blocked purely by client-side validation with no dialog.
     * The real pass/fail decision for both CREATE and REJECT rows is the DB check that follows,
     * not this method — it only keeps the popup from being stuck behind a modal error.
     */
    public AddAccountClassPage dismissSaveErrorIfPresent() {
        if (SeleniumUtils.isElementPresent(Loc.SAVE_ERROR_OK, 3)) {
            SeleniumUtils.click(Loc.SAVE_ERROR_OK, WaitType.CLICKABLE, "ACCOUNTCLASS_SAVE_ERROR_CLOSE");
        }
        return this;
    }

    /** Clicks the popup's X — returns the shared discard-confirmation dialog, same as voucher forms. */
    public DocumentMessageBox clickClose() {
        SeleniumUtils.click(Loc.CLOSE_BUTTON, WaitType.CLICKABLE, "ACCOUNTCLASS_CLOSE");
        return new DocumentMessageBox();
    }
}
