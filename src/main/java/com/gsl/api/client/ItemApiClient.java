package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.common.LookupValue;
import com.gsl.api.model.common.PricingData;
import com.gsl.api.model.item.ItemCreateRequest;
import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.api.model.item.ItemLookupData;
import com.gsl.enums.ItemManagementMode;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

/**
 * WebAPI/PublicAPI client for Item creation. Mirrors D:\PerformanceAutomationTesting\flows\createItem.js
 * (22 endpoint calls total in k6) — this client implements the endpoints needed to build a
 * correct create payload; the remaining lookup-only calls (image/barcode checks, category
 * prompts, tax group lookups, etc.) follow the exact same get()/post() pattern via ApiClient
 * and can be added the same way as the ones below when a test needs them.
 */
public class ItemApiClient extends ApiClient {

    public ItemApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** GET /udf/GetUDFConfigList — 1 of 21 k6 lookup calls. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchUdfConfigList() {
        Response response = getRaw("/udf/GetUDFConfigList");
        return response.jsonPath().getList("result");
    }

    /** POST /Shared/GetGSTHSNLookup */
    public int fetchDefaultHsnCode() {
        Response response = postRaw("/Shared/GetGSTHSNLookup", Map.of());
        List<Integer> codes = response.jsonPath().getList("result.code", Integer.class);
        return codes.isEmpty() ? 0 : codes.get(0);
    }

    /** POST /ItemMaster/GetGroupDefaultValues */
    public ItemLookupData fetchGroupDefaults(int inventoryGroupCode) {
        Response response = postRaw("/ItemMaster/GetGroupDefaultValues", Map.of("invgrp", LookupValue.ofCode(inventoryGroupCode)));
        Integer categoryCode = response.jsonPath().getInt("result.invcat1.code");
        return ItemLookupData.builder()
                .inventoryGroupCode(inventoryGroupCode)
                .inventoryCategoryCode(categoryCode != null ? categoryCode : 0)
                .build();
    }

    /** POST /ItemMaster/GetUnitOfMeasurement */
    public String fetchDefaultUnitName() {
        Response response = postRaw("/ItemMaster/GetUnitOfMeasurement", Map.of());
        String unit = response.jsonPath().getString("result[0].unitname");
        return unit != null ? unit : "NOS";
    }

    /** Aggregates the lookups actually needed to build a valid ItemCreateRequest. */
    public ItemLookupData fetchAllLookups(int inventoryGroupCode) {
        ItemLookupData groupDefaults = fetchGroupDefaults(inventoryGroupCode);
        int hsnCode = fetchDefaultHsnCode();
        String unitName = fetchDefaultUnitName();
        return ItemLookupData.builder()
                .inventoryGroupCode(inventoryGroupCode)
                .inventoryCategoryCode(groupDefaults.getInventoryCategoryCode())
                .hsnSacCode(hsnCode)
                .unitName(unitName)
                .build();
    }

    /** POST /ItemMaster/Create — the final save call. */
    public ItemCreateResponse createItem(ItemCreateRequest request) {
        return post("/ItemMaster/Create", request, ItemCreateResponse.class);
    }

    /** Orchestrates lookups + create, matching k6's runCreateItemFlow() end-to-end sequence. */
    public ItemCreateResponse createItemWithDefaults(String itemName, ItemManagementMode mode,
                                                       PricingData pricing, int inventoryGroupCode) {
        ItemLookupData lookups = fetchAllLookups(inventoryGroupCode);

        ItemCreateRequest request = ItemCreateRequest.builder()
                .cname1(itemName)
                .itemManagementMode(mode.itemManagementCode())
                .priceManagement(mode.priceManagementCode())
                .costrate(pricing.getCostrate())
                .wsp(pricing.getWsp())
                .mrp(pricing.getMrp())
                .listedMrp(pricing.getListedMrp())
                .unitname(lookups.getUnitName())
                .inventoryGroup(LookupValue.ofCode(lookups.getInventoryGroupCode()))
                .inventoryCategory(LookupValue.ofCode(lookups.getInventoryCategoryCode()))
                .hsnSacMain(LookupValue.ofCode(lookups.getHsnSacCode()))
                .build();

        return createItem(request);
    }
}
