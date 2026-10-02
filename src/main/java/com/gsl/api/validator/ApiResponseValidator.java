package com.gsl.api.validator;

import com.gsl.api.core.ApiException;

/**
 * Shared assertion primitives every domain {@code *ApiValidator} builds its {@code validate(...)}
 * method on, instead of each one hand-rolling its own success/error checks — see
 * {@link com.gsl.api.validator.GrcApiValidator}, {@link com.gsl.api.validator.DcApiValidator},
 * {@link com.gsl.api.validator.TransferOutApiValidator},
 * {@link com.gsl.api.validator.ArVoucherApiValidator},
 * {@link com.gsl.api.validator.ApVoucherApiValidator}, and
 * {@link com.gsl.api.validator.ServiceInvoiceAdhocApiValidator} for the common two-line shape:
 * {@code requireSuccess(...)} followed by one or more {@code requireNonBlank(...)} calls against
 * the response's key identifier field(s) (doc scheme, scheme docno, etc).
 *
 * <p>{@link com.gsl.api.validator.ItemApiValidator} is the one sibling that does NOT use this
 * class — it accumulates failures into an {@code ItemValidationResult} instead of throwing.
 */
public final class ApiResponseValidator {

    private ApiResponseValidator() {}

    /**
     * Asserts an API envelope's {@code success} flag is {@code true}.
     *
     * @param success whether the call reported success
     * @param error   the envelope's error message, folded into the failure message when not successful
     * @param context caller-supplied label (e.g. {@code "GRC/Save"}) identifying which call failed
     * @throws ApiException if {@code success} is {@code false}
     */
    public static void requireSuccess(boolean success, String error, String context) {
        if (!success) {
            throw new ApiException(context + " returned success=false: " + error, 0);
        }
    }

    /**
     * Asserts a required response field is present and non-blank (e.g. a returned document/scheme code).
     *
     * @param value     the field's value to check
     * @param fieldName the field's name, used only in the failure message
     * @param context   caller-supplied label (e.g. {@code "GRC/Save"}) identifying which call failed
     * @throws ApiException if {@code value} is {@code null} or blank
     */
    public static void requireNonBlank(String value, String fieldName, String context) {
        if (value == null || value.isBlank()) {
            throw new ApiException(context + " — required field '" + fieldName + "' is blank", 0);
        }
    }
}
