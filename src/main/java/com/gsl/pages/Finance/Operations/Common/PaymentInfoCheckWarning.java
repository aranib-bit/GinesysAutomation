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

    AR_VOUCHER("receipt voucher",  "Ignore & Proceed", null,  null),
    AP_VOUCHER("payment voucher",  "Yes",              "No",  null);

    private final String messageSubstring;  // substring of the warning message to identify this type
    private final String proceedLabel;      // "Ignore & Proceed" (AR) or "Yes" (AP)
    private final String rejectLabel;       // null = not available; "No" for AP
    private final String cancelLabel;       // reserved — null for now

    PaymentInfoCheckWarning(String messageSubstring, String proceedLabel,
                            String rejectLabel, String cancelLabel) {
        this.messageSubstring = messageSubstring;
        this.proceedLabel     = proceedLabel;
        this.rejectLabel      = rejectLabel;
        this.cancelLabel      = cancelLabel;
    }

    public String getMessageSubstring() { return messageSubstring; }

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
