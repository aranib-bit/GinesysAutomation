package com.gsl.api.webapi.model.response.tds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of a TDS master list: {@code result.items}. */
@Getter
@Setter
public class TdsSourceListResponse extends ApiEnvelopeBase {
    private Page result;

    public List<TdsSourceItem> items() {
        return result == null || result.getItems() == null ? List.of() : result.getItems();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Page {
        private List<TdsSourceItem> items;
    }
}
