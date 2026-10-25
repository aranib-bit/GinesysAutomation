package com.gsl.validation.ui.voucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * UI contract of the Payment Information popup (live screenshot, 2026-10-06): window title, the "Payment
 * Information Allocate" section, Payment Mode (mandatory dropdown), Instrument No. (dropdown), Instrument
 * Date (date field), Cheque label / Drawn On, Reference, and OK / Cancel. Every field gets the full property
 * set; expectations (element type, mandatory, state) come from the Excel baseline.
 */
public final class PaymentPopupUi {

    private final PopupUi popup = new PopupUi("Payment Information");
    private final String drawnOnLabel;

    public PaymentPopupUi(VoucherDomain domain) {
        this.drawnOnLabel = domain.getPaymentDrawnOnLabel();
    }

    /** The popup as opened, before anything is filled. */
    public List<UiValidationFailure> beforeFill(UiValidationLevel level) {
        List<UiValidationFailure> f = new ArrayList<>(popup.windowTitle());
        f.addAll(popup.sectionHeader("PAYMENT_POPUP_SECTION_PAYMENT_INFORMATION_ALLOCATE", "Payment Information Allocate", level));
        f.addAll(popup.field("PAYMENT_POPUP_PAYMENT_MODE", "Payment Mode:", PopupUi.BEFORE, FieldStyleSpec.LOOKUP_INPUT, level));
        f.addAll(popup.field("PAYMENT_POPUP_INSTRUMENT_NO", "Instrument No.:", PopupUi.BEFORE, FieldStyleSpec.LOOKUP_INPUT, level));
        f.addAll(popup.field("PAYMENT_POPUP_INSTRUMENT_DATE", "Instrument Date:", PopupUi.BEFORE, FieldStyleSpec.DATE_INPUT, level));
        f.addAll(popup.field("PAYMENT_POPUP_CHEQUE_LABEL", drawnOnLabel, PopupUi.BEFORE, FieldStyleSpec.TEXT_INPUT, level));
        f.addAll(popup.field("PAYMENT_POPUP_REFERENCE", "Reference:", PopupUi.BEFORE, FieldStyleSpec.TEXT_INPUT, level));
        f.addAll(popup.button("PAYMENT_POPUP_OK_BUTTON", "OK", level));
        f.addAll(popup.button("PAYMENT_POPUP_CANCEL_BUTTON", "Cancel", level));
        return f;
    }

    /** After the fields are filled, before OK: every field's value read back and compared with what was entered
     *  ({@code null} expected = field not filled by the scenario, value only recorded) — one row per field. */
    public List<UiValidationFailure> afterFill(UiValidationLevel level, String mode, String instrumentNo,
            String instrumentDate, String drawnOn, String reference) {
        List<UiValidationFailure> f = new ArrayList<>();
        f.addAll(field("PAYMENT_POPUP_PAYMENT_MODE", "Payment Mode:", mode, level));
        f.addAll(field("PAYMENT_POPUP_INSTRUMENT_NO", "Instrument No.:", instrumentNo, level));
        // Instrument No. changes kind with the payment mode (AP cheque: text box that may become the cheque-register
        // dropdown once filled; NEFT/RTGS and all AR: text box). Baselined per mode: PAYMENT_POPUP_INSTRUMENT_NO_<MODE>.
        if (mode != null && !mode.isBlank()) {
            f.addAll(UiComponentValidator.validateElementTypeBaselined(popup.inputOf("Instrument No.:"),
                    "PAYMENT_POPUP_INSTRUMENT_NO_" + PopupUi.key(mode)));
        }
        f.addAll(field("PAYMENT_POPUP_INSTRUMENT_DATE", "Instrument Date:", instrumentDate, level));
        f.addAll(field("PAYMENT_POPUP_CHEQUE_LABEL", drawnOnLabel, drawnOn, level));
        f.addAll(field("PAYMENT_POPUP_REFERENCE", "Reference:", reference, level));
        return f;
    }

    private List<UiValidationFailure> field(String component, String label, String expected, UiValidationLevel level) {
        String actual = UiComponentValidator.readValue(popup.inputOf(label));
        List<UiValidationFailure> r = new ArrayList<>();
        r.addAll(UiComponentValidator.validateEquals(component, "value after fill",
                expected == null ? actual : expected, actual));
        if (level == UiValidationLevel.CONTRACT) {
            // A field the app greys out for this payment mode (e.g. Instrument Date) legitimately has the
            // read-only look; judge style against the state the field really has, not an assumed EDITABLE.
            com.gsl.utils.ui.UiFieldState actualState = com.gsl.utils.ui.UiFieldState.EDITABLE;
            try {
                actualState = com.gsl.validation.ui.FieldStateProbe.state(
                        com.gsl.driver.DriverManager.getDriver().findElement(popup.inputOf(label)));
            } catch (RuntimeException ignored) {
                // not readable - keep the editable expectation
            }
            boolean editable = actualState == com.gsl.utils.ui.UiFieldState.EDITABLE;
            r.addAll(UiComponentValidator.validateInputStyle(popup.inputOf(label), component,
                    editable ? FieldStyleSpec.TEXT_INPUT : FieldStyleSpec.READONLY_AMOUNT, actualState, level));
        }
        return r;
    }
}
