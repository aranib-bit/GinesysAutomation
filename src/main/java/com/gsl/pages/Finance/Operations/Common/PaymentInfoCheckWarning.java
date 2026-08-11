package com.gsl.pages.Finance.Operations.Common;

import org.openqa.selenium.By;

/**
 * Identifies the payment-information-missing warning dialog variant shown on AP/AR Voucher save.
 *
 * <p>AR Voucher: "This is a receipt voucher and you have not specified the instrument number.
 *                Do you want to proceed without instrument number?" — [Ignore &amp; Proceed]
 * <p>AP Voucher: "This is a payment voucher and you have not specified the instrument number.
 *                Do you want to proceed without instrument number?" — [Yes] [No]
 *
 * <p>Use with {@link DocumentPopup#verifyPaymentInfoCheckWarning},
 * {@link DocumentPopup#clickProceed}, {@link DocumentPopup#clickReject}, and
 * {@link DocumentPopup#clickCancel}.
 */
public enum PaymentInfoCheckWarning {

    AR_VOUCHER("receipt voucher",  "Ignore & Proceed", null,  null, false),
    AP_VOUCHER("payment voucher",  "Yes",              "No",  null, true),
    /**
     * Wording not yet confirmed against the live UI (General Voucher has no
     * screenshot of this dialog) — defaulted to AP's Yes/No shape as the best
     * guess until verified. See plan's "Verification / Open items" §3.
     */
    GV_VOUCHER("voucher",          "Yes",              "No",  null, false);

    private final String messageSubstring;  // substring of the warning message to identify this type
    private final String proceedLabel;      // "Ignore & Proceed" (AR) or "Yes" (AP)
    private final String rejectLabel;       // null = not available; "No" for AP
    private final String cancelLabel;       // reserved — null for now
    private final boolean rendersBoldStopMarker;

    PaymentInfoCheckWarning(String messageSubstring, String proceedLabel,
                            String rejectLabel, String cancelLabel, boolean rendersBoldStopMarker) {
        this.messageSubstring = messageSubstring;
        this.proceedLabel     = proceedLabel;
        this.rejectLabel      = rejectLabel;
        this.cancelLabel      = cancelLabel;
        this.rendersBoldStopMarker = rendersBoldStopMarker;
    }

    public String getMessageSubstring() { return messageSubstring; }

    /**
     * Whether this voucher type's inline row QuickTip renders the blocking "Stop:" severity
     * marker as bold HTML ({@code <b>Stop:</b>}) — confirmed live for AP (see
     * {@code NegativeValidation.ZERO_AMOUNT_TOOLTIP_STOP_BOLD}); AR confirmed not to need the
     * check today (its one inline-validation scenario never asserted it, not proof the marker
     * is never bold there); GV unconfirmed either way. Used by
     * {@code NegativeValidationSteps.assertRowTooltip} to decide whether to additionally call
     * {@code ExtJsQuickTipUtils.verifyVisibleHtml}.
     */
    public boolean rendersBoldStopMarker() { return rendersBoldStopMarker; }

    public By proceedLocator() {
        return By.xpath("//span[normalize-space()='" + proceedLabel + "']");
    }

    public boolean hasRejectButton()  { return rejectLabel != null; }

    public By rejectLocator() {
        if (rejectLabel == null)
            throw new UnsupportedOperationException(this + " has no reject button");
        return By.xpath("//span[normalize-space()='" + rejectLabel + "']");
    }

    public boolean hasCancelButton() { return cancelLabel != null; }

    public By cancelLocator() {
        if (cancelLabel == null)
            throw new UnsupportedOperationException(this + " has no cancel button");
        return By.xpath("//span[normalize-space()='" + cancelLabel + "']");
    }
}
