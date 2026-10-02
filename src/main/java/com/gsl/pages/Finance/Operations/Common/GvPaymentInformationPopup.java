package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import org.openqa.selenium.By;

/**
 * General Voucher-specific Payment Information popup for a <b>Dr-side (Debit/Payment)</b>
 * Cash/Bank line — see {@link com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail
 * #createPaymentPopup(boolean)} for the Dr/Cr dispatch. {@link GvReceiptPaymentInformationPopup}
 * is this class's Cr-side (Credit/Receipt) sibling, used instead for a Cr line — confirmed live in
 * idealdb that GV's cheque-book participation (bookcode/finchqdet) only ever happens on the Dr
 * side, exactly like AP Voucher; a Cr line has no bookcode/finchqdet involvement at all, exactly
 * like AR Voucher (VG/02556/26-27 vs. VG/02597/26-27).
 *
 * <p><b>Extends {@link ChequeRegisterPaymentInformationPopup}</b> (AP's shape), not {@code
 * ArPaymentInformationPopup}'s simpler always-auto-populate shape — correct for a Dr line because
 * of how {@code instrumentNo} test data is sourced: GV's {@code CHEQUE} variant draws it from
 * {@code GeneralVoucherTemplateLoader.combinationTemplate} →
 * {@code UniqueChequeGenerator.generateUnique()}, the exact same generator AP's DB-driven
 * scenarios use — a synthetic, never-registered value. Every GV Dr-side CHEQUE combination
 * therefore always lands on the "unknown cheque" branch, which requires the known/unknown
 * handling (and the in-popup "not present in system" warning dismissal) that only {@link
 * ChequeRegisterPaymentInformationPopup} implements; an AR-style unconditional {@code
 * setInstrumentDate} call fails on this path because the Instrument Date field is disabled on the
 * unknown-cheque path, and {@code ArPaymentInformationPopup}'s shape has no way to detect that —
 * this class was previously applied unconditionally to every GV line regardless of Dr/Cr, which
 * silently broke Cr-side lines; {@link GvReceiptPaymentInformationPopup} now covers those. Its
 * "Drawn On" field (confirmed live to actually be labeled "Cheque label:", same as AP — NOT
 * "Drawn on:" as a prior, never-live-verified guess had it) is now handled by the shared
 * {@link PaymentInformationPopup#setDrawnOn(String)}, driven by {@link
 * VoucherDomain#getPaymentDrawnOnLabel()} — this class only supplies its window-scoped OK button.
 */
public class GvPaymentInformationPopup extends ChequeRegisterPaymentInformationPopup {

    public GvPaymentInformationPopup() {
        super(VoucherDomain.GV);
    }

    /**
     * Confirmed live (reports/runs/20260913_141449, FIN-GV-ADD-COMBO-07 screenshots): this
     * popup's own window title reads "Payment Information" — same {@code winTitleType2} class
     * {@code AllocateSitesPopup}/{@code ReferenceSiteWiseFIFOPopup} already key off. Scoping OK
     * to this window (rather than {@link PaymentInformationPopup#OK_BUTTON}'s unscoped default)
     * closes the failure where a leftover/stale Allocate Sites window — GV's own preceding step,
     * unique to GV among the voucher domains — matched the base class's global {@code x-window}
     * OK-button xpath first, so Selenium kept polling that stale element and never saw this
     * popup's own, genuinely visible/clickable OK button.
     *
     * <p>Package-visible (not {@code private}) so {@link GvReceiptPaymentInformationPopup} — the
     * Cr-side sibling, which needs this exact same window-scoping fix — can reuse this literal
     * instead of duplicating it; the window-scoping issue is unrelated to Dr/Cr, both GV popups
     * are the same "Payment Information" window.
     */
    private static final String WIN_TITLE = "Payment Information";

    static final By OK_BUTTON_SCOPED = By.xpath(
            "//div[contains(@class,'x-window') and @role='dialog' and "
            + ".//div[contains(@class,'winTitleType2') and normalize-space()='" + WIN_TITLE + "']]"
            + "//span[normalize-space()='OK']/ancestor::a[contains(@class,'x-btn') and not(@aria-hidden='true')]");

    @Override
    protected By okButtonLocator() {
        return OK_BUTTON_SCOPED;
    }

    /** User-confirmed live: on this Dr/Cheque popup Instrument Date is DISABLED until the cheque is
     *  known (it is then filled by the app). The Cr/receipt sibling is not confirmed, so it keeps the
     *  base default (state not asserted). */
    @Override
    protected com.gsl.utils.ui.UiFieldState instrumentDateStateBeforeFill() {
        return com.gsl.utils.ui.UiFieldState.DISABLED;
    }
}
