package com.gsl.api.model.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated result of ItemApiClient's pre-create lookup calls (GetUDFConfigList,
 * GetGSTHSNLookup, GetGroupDefaultValues, GetUnitOfMeasurement, etc. — see
 * flows/createItem.js) — just the values actually needed to build a valid
 * ItemCreateRequest, not the full raw response of every one of the 21 lookup endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemLookupData {
    private int inventoryGroupCode;
    private int inventoryCategoryCode;
    private int hsnSacCode;
    private String unitName;
}
