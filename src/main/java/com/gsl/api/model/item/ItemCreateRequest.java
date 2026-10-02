package com.gsl.api.model.item;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.common.LookupValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST ItemMaster/Create request body. Field set mirrors
 * D:\PerformanceAutomationTesting\flows\createItem.js — core fields only; additional
 * udfstring/udfnum fields follow the same pattern and can be added as needed. Built by
 * {@code ItemApiClient.createItemWithDefaults(...)} from a {@link com.gsl.enums.ItemManagementMode}
 * (supplies {@code itemManagementMode}/{@code priceManagement}) and an {@link ItemLookupData}
 * (supplies {@code unitname}/{@code inventoryGroup}/{@code inventoryCategory}/{@code hsnSacMain}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemCreateRequest {

    @Builder.Default
    private String code = "";
    @Builder.Default
    private String barcode = "";
    @Builder.Default
    private String materialType = "F";
    @Builder.Default
    private String noninventory = "N";

    private String itemManagementMode; // "I" | "B" | "S"
    private String priceManagement;    // "I" | "B"

    private String cname1;             // item name, <=40 chars

    private Integer costrate;
    private Integer wsp;
    private Integer mrp;
    private Integer listedMrp;

    @Builder.Default
    private Integer cmpcode = 1;
    private String unitname;

    @JsonProperty("invgrp")
    private LookupValue inventoryGroup;
    @JsonProperty("invcat1")
    private LookupValue inventoryCategory;
    @JsonProperty("invhsnsacmain")
    private LookupValue hsnSacMain;
}
