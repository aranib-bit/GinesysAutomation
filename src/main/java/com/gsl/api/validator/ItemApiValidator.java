package com.gsl.api.validator;

import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.api.model.item.ItemValidationResult;

/** Validates an ItemMaster/Create response: success + a non-blank returned item code. */
public final class ItemApiValidator {

    private ItemApiValidator() {}

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
