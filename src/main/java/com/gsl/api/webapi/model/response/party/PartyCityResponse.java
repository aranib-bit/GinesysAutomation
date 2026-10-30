package com.gsl.api.webapi.model.response.party;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code Shared/GetCityLookup}: {@code result.items[]} with the city name in {@code code}. */
@Getter
@Setter
public class PartyCityResponse extends ApiEnvelopeBase {
    private Result result;

    public List<Item> items() {
        return result == null || result.getItems() == null ? List.of() : result.getItems();
    }

    public int totalCount() {
        return result == null ? 0 : result.getTotalCount();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private int totalCount;
        private List<Item> items;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** The city name. */
        @JsonProperty("code")
        private String name;
        @JsonProperty("stateName")
        private String state;
        @JsonProperty("country")
        private String country;
        @JsonProperty("ext")
        private String extinct;
    }
}
