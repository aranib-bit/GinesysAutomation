package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page component for the General Voucher "Document Information" section.
 *
 * <p>Thinner than AP's subclass — General Voucher has no "On Account" concept,
 * so {@link #fillDocumentHeader} simply delegates to the inherited
 * {@code fillCommonFields} + sets Remarks.
 *
 * <p><b>UNVERIFIED (see plan's "Verification / Open items" §1):</b> the
 * read-only fields below ({@link #getBalance()}, {@link #getNetAmount()},
 * {@link #getReleasedStatus()}) are screenshot-only
 * fields not present in AP/AR's document-info section — locators are best-effort
 * placeholders, same style {@code ApVoucherListPage} already uses for its own
 * unverified elements, pending live DOM inspection.
 */
public class GeneralVoucherDocumentInformation extends VoucherDocumentInformationBase {

    // ── TODO: confirm against the live UI — placeholder locators, not yet verified ──
    private static final By BALANCE_DISPLAY = By.xpath(
            "//span[contains(@class,'x-form-item-label-text') and normalize-space()='Balance:']" +
            "/ancestor::div[contains(@class,'x-form-item')]//input[contains(@id,'-inputEl')]");

    // Fixed (2026-10-01) — THREE prior attempts missed before the user supplied the exact live
    // DOM pair:
    //   <label class="x-component x-column x-component-default" ...>Net Amount:</label>
    //   <label class="x-component gsl_FieldLabel x-column x-component-default"
    //          style="...font-weight:bold;...">22.00 Cr (Payment)</label>
    // — the label IS a <label> (attempts 1-2 assumed <span>) and the value IS the immediate
    // following-sibling <label> (attempt 3's axis was right), but attempt 3's blind
    // following-sibling::*[1] would silently resolve to the WRONG node (e.g. an icon element) if
    // one ever renders between the two labels, instead of throwing — the value carries its own
    // distinctive gsl_FieldLabel class, so key on that specifically rather than "whatever's next".
    // Confirmed the SAME <label>+gsl_FieldLabel component pair is what AP's "Payment Amount :" and
    // AR's "Receipt Amount :" fields use too (user-supplied HTML for both) — only the leading
    // label text differs per domain (a business difference, not a styling one) — reuse this exact
    // locator PATTERN there once AP/AR wire up their own equivalent field.
    private static final By NET_AMOUNT_DISPLAY = By.xpath(
            "//label[normalize-space()='Net Amount:']"
            + "/following-sibling::label[contains(@class,'gsl_FieldLabel')][1]");

    /** The "Net Amount:" LABEL itself — distinct from {@link #NET_AMOUNT_DISPLAY} (its value
     *  sibling). Unlike the value (absent until a detail row exists), this label is present and
     *  styled from the moment the Add form opens. */
    private static final By NET_AMOUNT_LABEL = By.xpath("//label[normalize-space()='Net Amount:']");

    private static final By RELEASED_STATUS_DISPLAY = By.xpath(
            "//span[contains(@class,'x-status') and (normalize-space()='RELEASED' or normalize-space()='UNRELEASED')]");

    /** Round 23: the read-only "Voucher No.:" field on the Add/Edit form — confirms the
     *  already-saved document's number is showing (the on-screen label reads "Voucher No.:" for
     *  GV, but what this validates is "the saved document no", not "a" voucher number). Renders as
     *  a {@code <div role="textbox">} ExtJS displayfield, not an {@code <input>} — same
     *  label-anchored xpath idiom as {@link #BALANCE_DISPLAY}/{@code VoucherDocumentInformationBase
     *  #ownerSiteDisplay}, generalized from {@code //input[contains(@id,'-inputEl')]} to
     *  {@code //*[contains(@id,'-inputEl')]} since a div has no {@code value} attribute to anchor
     *  an input-only predicate on. Never the ExtJS auto-generated numeric
     *  {@code displayfield-NNNN} id, confirmed unstable run to run.
     *
     *  <p><b>Round 24 — confirmed genuinely ambiguous, not just tag-generic:</b> live DOM captures
     *  (run {@code 20260927_011136}) show THREE separate fields labeled "Voucher No.:" on this
     *  form — an auto/lookup combo and a manual-doc-scheme textfield (both
     *  {@code display:none}, one or the other rendered depending on doc-numbering-scheme config),
     *  plus this real, visible, readonly displayfield. This locator matches all three; resolving
     *  it must pick whichever one is genuinely visible (see {@link #resolveSavedDocumentNoElement()}
     *  via {@link WaitUtils#untilAnyVisible(By, long)}), never just the first DOM match — the first
     *  match is the permanently-hidden combo, which made every Edit test fail deterministically
     *  until this fix. */
    private static final By SAVED_DOCUMENT_NO_DISPLAY = By.xpath(
            "//span[contains(@class,'x-form-item-label-text') and normalize-space()='Voucher No.:']" +
            "/ancestor::div[contains(@class,'x-form-item')]//*[contains(@id,'-inputEl')]");

    public GeneralVoucherDocumentInformation(CashBankParent parentPage) {
        super(parentPage, VoucherDomain.GV);
    }

    public GeneralVoucherDocumentInformation fillDocumentHeader(DocumentInformation docInfo) {
        fillCommonFields(docInfo);
        setRemarks(docInfo.getRemarks());
        return this;
    }

    /** Reads the read-only "Balance:" field (Cash/Bank ledger's current balance). */
    public String getBalance() {
        return SeleniumUtils.getAttribute(BALANCE_DISPLAY, "value", "CASH_BANK_BALANCE");
    }

    /** Reads the "Net Amount:" text, including its Dr/Cr + Payment/Receipt suffix (e.g.
     *  confirmed live "22.00 Cr (Payment)"). Throws (via {@link SeleniumUtils#getAttribute}'s own
     *  wait) if the value isn't rendered yet (no Expense/Income Detail rows) — callers on a form
     *  that may still be blank should check presence first (see {@link #validateNetAmountUi}'s
     *  {@code expectValuePresent=false} path), not call this speculatively. */
    public String getNetAmount() {
        return SeleniumUtils.getAttribute(NET_AMOUNT_DISPLAY, "textContent", "GV_NET_AMOUNT");
    }

    /** Matches {@link #getNetAmount()}'s raw text, e.g. {@code "22.00 Cr (Payment)"}: group 1 is
     *  the numeric amount, group 2 the Dr/Cr letter, group 3 the Payment/Receipt label. */
    private static final Pattern NET_AMOUNT_PATTERN =
            Pattern.compile("((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})\\s*(Dr|Cr)\\s*\\((Payment|Receipt)\\)");

    /** The Net Amount's numeric value, parsed from {@link #getNetAmount()} (which must already be
     *  rendered — see that method's Javadoc) — {@code null} only if the rendered text doesn't
     *  match the expected format (a genuine parsing/format surprise, not an absence). */
    public BigDecimal getNetAmountValue() {
        Matcher matcher = matchNetAmount();
        // Thousands separators ("1,801.00 Cr (Payment)") are part of the amount — strip before parsing.
        return matcher == null ? null : new BigDecimal(matcher.group(1).replace(",", ""));
    }

    /** The Net Amount's "Payment"/"Receipt" label, parsed from {@link #getNetAmount()} —
     *  {@code null} under the same condition as {@link #getNetAmountValue()}. */
    public String getNetAmountLabel() {
        Matcher matcher = matchNetAmount();
        return matcher == null ? null : matcher.group(3);
    }

    private Matcher matchNetAmount() {
        Matcher matcher = NET_AMOUNT_PATTERN.matcher(getNetAmount());
        return matcher.find() ? matcher : null;
    }

    /** Reads the "RELEASED"/"UNRELEASED" status badge. */
    public String getReleasedStatus() {
        return SeleniumUtils.getAttribute(RELEASED_STATUS_DISPLAY, "textContent", "GV_RELEASED_STATUS");
    }

    /** Round 24 — resolves {@link #SAVED_DOCUMENT_NO_DISPLAY} to whichever of its 3 candidate
     *  matches is genuinely visible right now, not just the first DOM match (which is a
     *  permanently-hidden field — see that locator's own Javadoc). */
    private WebElement resolveSavedDocumentNoElement() {
        return WaitUtils.untilAnyVisible(SAVED_DOCUMENT_NO_DISPLAY, ConfigFactory.getConfig().timeout());
    }

    /** Reads the read-only "Voucher No.:" field — the already-saved document's number. Read via
     *  {@code textContent}, not {@code value}: this is a {@code <div role="textbox">} ExtJS
     *  displayfield, not an {@code <input>}, so it has no {@code value} attribute — same idiom as
     *  {@link #getNetAmount()}, not {@link #getBalance()}. Deliberately independent of read-only
     *  state (Round 24) — this confirms the correct document loaded; see
     *  {@link #isSavedDocumentNoReadOnly()} for the separate read-only assertion. */
    public String getSavedDocumentNo() {
        return SeleniumUtils.getAttribute(resolveSavedDocumentNoElement(), "textContent", "GV_SAVED_DOCUMENT_NO");
    }

    /** Round 24 — whether the currently-visible "Voucher No.:" field is genuinely read-only
     *  ({@code aria-readonly="true"}). A separate, dedicated check from {@link #getSavedDocumentNo()}
     *  so a regression that makes this field editable is reported with its own clear diagnosis
     *  instead of masking, or being confused with, a "wrong document opened" failure. */
    public boolean isSavedDocumentNoReadOnly() {
        String ariaReadonly = SeleniumUtils.getAttribute(
                resolveSavedDocumentNoElement(), "aria-readonly", "GV_SAVED_DOCUMENT_NO_ARIA_READONLY");
        return "true".equalsIgnoreCase(ariaReadonly);
    }

    /** Live computed font-size/font-family of the currently-visible "Voucher No.:" field (Round 23
     *  — page-styling regression check; Round 24 — resolved via {@link #resolveSavedDocumentNoElement()}
     *  so this measures the real visible field, not whichever hidden candidate is first in DOM order). */
    public String getSavedDocumentNoFont() {
        return SeleniumUtils.getComputedFont(resolveSavedDocumentNoElement(), "GV_SAVED_DOCUMENT_NO");
    }

    // ── UI-contract validation — GV-only fields (Balance/Net Amount/Released Status have no
    // shared-base equivalent; every other Document Information field's validate*Ui method lives on
    // VoucherDocumentInformationBase itself — see that class's own UI-contract section). ──────────

    /** UI-contract check for the "Cash/Bank Balance:" field (the on-screen label is "Balance:").
     *  Confirmed live (user screenshot): its background is greyed ({@code DISABLED} style) before
     *  Cash/Bank is selected and shows the normal readonly-amount style once a Cash/Bank is set.
     *  Business rule (user): the field is NEVER editable — not before, and not after Cash/Bank is
     *  selected — so the not-editable state check runs in both phases; only the expected
     *  background differs. The Actual column shows the real state (DISABLED/READONLY).
     *
     *  @param cashBankSelected {@code false} on a blank Add form, {@code true} after Cash/Bank is
     *         chosen (and for any Edit of a saved voucher) */
    public List<UiValidationFailure> validateBalanceUi(UiValidationLevel level, boolean cashBankSelected) {
        UiFieldState styleState = cashBankSelected ? null : UiFieldState.DISABLED;
        List<UiValidationFailure> failures = new java.util.ArrayList<>(UiComponentValidator.validateReadonlyAmount(
                BALANCE_DISPLAY, "CASH_BANK_BALANCE", FieldStyleSpec.READONLY_AMOUNT, styleState, level));
        failures.addAll(UiComponentValidator.validateNotEditable(BALANCE_DISPLAY, "CASH_BANK_BALANCE"));
        return failures;
    }

    /** Net Amount checkpoint after a grid row is filled: value and Payment/Receipt label against
     *  the expected (computed by the caller from the test rows, never read back to derive it) plus
     *  the emphasized-value style. {@code checkpoint} names the state, e.g. "after Row 2". */
    public List<UiValidationFailure> validateNetAmountAfterRowUi(UiValidationLevel level, BigDecimal expectedAmount,
            String expectedLabel, String checkpoint) {
        String component = "GV_NET_AMOUNT (" + checkpoint + ")";
        List<UiValidationFailure> failures = new java.util.ArrayList<>(
                UiComponentValidator.validateReadonlyAmount(NET_AMOUNT_DISPLAY, component,
                        FieldStyleSpec.AMOUNT_VALUE_LABEL, level));
        BigDecimal actualAmount = null;
        String actualLabel = null;
        try {
            actualAmount = getNetAmountValue();
            actualLabel = getNetAmountLabel();
        } catch (RuntimeException e) {
            // value not rendered — recorded as a failed comparison below, not a crash
        }
        String expectedAmountText = expectedAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        failures.add(new UiValidationFailure(component, "value", expectedAmountText,
                actualAmount == null ? "NOT FOUND" : actualAmount.toPlainString(),
                actualAmount != null && actualAmount.compareTo(expectedAmount) == 0
                        ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        failures.add(new UiValidationFailure(component, "label", expectedLabel,
                actualLabel == null ? "NOT FOUND" : actualLabel,
                expectedLabel.equalsIgnoreCase(actualLabel)
                        ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        return failures;
    }

    /** UI-contract check for the "Net Amount:" LABEL itself — unlike the value (see
     *  {@link #validateNetAmountUi}), this label is present and styled from the moment the Add
     *  form opens, before any Expense/Income Detail row exists. Checked unconditionally (both Add
     *  and Edit). */
    public List<UiValidationFailure> validateNetAmountLabelUi(UiValidationLevel level) {
        return UiComponentValidator.validateLabel(NET_AMOUNT_LABEL, "GV_NET_AMOUNT_LABEL",
                "Net Amount:", FieldStyleSpec.PAIRED_AMOUNT_LABEL, level);
    }

    /** UI-contract check for the "Net Amount:" value. Confirmed live (2026-10-01, user-supplied
     *  business rule): the value only renders once at least one Expense/Income Detail grid row
     *  exists — a blank Add form's "Net Amount:" is a plain label with no value sibling at all, not
     *  a transient rendering delay. {@code expectValuePresent} lets callers assert the CORRECT state
     *  either way instead of treating "no rows yet" as a failure:
     *  <ul>
     *    <li>{@code false} (blank Add form) — asserts the value is correctly ABSENT via
     *        {@link UiComponentValidator#validateElementAbsent}.</li>
     *    <li>{@code true} (Edit, a real saved voucher — guaranteed at least one detail row) —
     *        the existing style check against the emphasized
     *        {@link FieldStyleSpec#AMOUNT_VALUE_LABEL} role (bold, transparent background, distinct
     *        from a plain {@link FieldStyleSpec#READONLY_AMOUNT} like Balance).</li>
     *  </ul> */
    public List<UiValidationFailure> validateNetAmountUi(UiValidationLevel level, boolean expectValuePresent) {
        if (!expectValuePresent) {
            return UiComponentValidator.validateElementAbsent(NET_AMOUNT_DISPLAY, "GV_NET_AMOUNT");
        }
        return UiComponentValidator.validateReadonlyAmount(
                NET_AMOUNT_DISPLAY, "GV_NET_AMOUNT", FieldStyleSpec.AMOUNT_VALUE_LABEL, level);
    }

    /** Cross-checks the on-screen RELEASED/UNRELEASED badge against the status the caller believes
     *  the document has (Save is shown only for an Unposted document, so a mismatch here would
     *  otherwise make the footer expectation silently wrong). Badge not displayed = SKIP, not FAIL
     *  (its presence on every form is not confirmed live). */
    public List<UiValidationFailure> validateReleaseStatusConsistency(boolean expectPosted) {
        if (!UiComponentValidator.quickPresent(RELEASED_STATUS_DISPLAY)) {
            return UiComponentValidator.skip("GV_RELEASED_STATUS", "status badge",
                    "validated if shown", "not displayed");
        }
        String expected = expectPosted ? "RELEASED" : "UNRELEASED";
        return UiComponentValidator.validateStatusIndicator(RELEASED_STATUS_DISPLAY, "GV_RELEASED_STATUS",
                expected, UiValidationLevel.SMOKE);
    }

    /** UI-contract check for the "RELEASED"/"UNRELEASED" status badge. */
    public List<UiValidationFailure> validateReleasedStatusUi(UiValidationLevel level, String expectedStatus) {
        return UiComponentValidator.validateStatusIndicator(RELEASED_STATUS_DISPLAY, "GV_RELEASED_STATUS",
                expectedStatus, level);
    }
}
