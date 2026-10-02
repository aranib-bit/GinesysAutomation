package com.gsl.api.validator;

import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.api.model.item.ItemValidationResult;

/**
 * Validates an {@code ItemMaster/Create} response: the envelope's {@code success} flag must be
 * {@code true}, and the response must carry a non-blank item code.
 *
 * <p>Unlike its sibling {@code *ApiValidator} classes (e.g. {@link GrcApiValidator},
 * {@link DcApiValidator}), this one does not throw via {@link ApiResponseValidator} — it returns
 * an {@link ItemValidationResult} pass/fail outcome instead, for callers that need to inspect or
 * report the failure reason rather than have it propagate as an exception.
 */
public final class ItemApiValidator {

    private ItemApiValidator() {}

    /** @return {@link ItemValidationResult#pass()} if valid, otherwise a fail result naming the reason. */
    public static ItemValidationResult validate(ItemCreateResponse response) {
        if (!response.isSuccess()) {
            return ItemValidationResult.fail("success=false, error=" + response.getError());
        }
        if (response.getItemCode() == null || response.getItemCode().isBlank()) {
            return ItemValidationResult.fail("success=true but no item code returned in result");
        }
        return ItemValidationResult.pass();
    }
}
