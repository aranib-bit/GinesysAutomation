package com.gsl.api.model.item;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ItemMaster/Create's {result, success, error} envelope, deserialized whole. The actual item
 * fields live under {@code result} (confirmed envelope shape — see e.g. Session/Logout's
 * {"result":null,"success":true,"error":null}); convenience getters below delegate into it so
 * callers can write {@code response.getItemCode()} without reaching through .getResult() first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemCreateResponse {

    private boolean success;
    private String error;
    private Result result;

    /** {@code result.code} (the created item's code), or null if the response has no result. */
    public String getItemCode()   { return result != null ? result.itemCode : null; }
    /** {@code result.cname1} (the item name actually saved), or null if the response has no result. */
    public String getCname1()     { return result != null ? result.cname1 : null; }
    /** {@code result.costrate}, or null if the response has no result. */
    public Integer getCostrate()  { return result != null ? result.costrate : null; }
    /** {@code result.wsp}, or null if the response has no result. */
    public Integer getWsp()       { return result != null ? result.wsp : null; }
    /** {@code result.mrp}, or null if the response has no result. */
    public Integer getMrp()       { return result != null ? result.mrp : null; }

    /** Raw shape of ItemMaster/Create's {@code result} object — only the fields this client consumes. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("code")
        private String itemCode;
        private String cname1;
        private Integer costrate;
        private Integer wsp;
        private Integer mrp;
    }
}
