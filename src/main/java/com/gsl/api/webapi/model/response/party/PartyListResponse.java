package com.gsl.api.webapi.model.response.party;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of the party list calls: {@code result.items} (one page) and {@code result.totalCount}. */
@Getter
@Setter
public class PartyListResponse extends ApiEnvelopeBase {
    private Result result;

    public List<PartyListItem> items() {
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
        private List<PartyListItem> items;
    }
}
