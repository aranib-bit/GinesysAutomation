package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code POST ApprovalEntityConfig/GetEntityConfigs}: one row per approval-capable entity. */
@Getter
@Setter
public class ApprovalEntityConfigsResponse extends ApiEnvelopeBase {
    private List<Item> result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String entityCode;
        private String displayName;
        /** Wrapper on purpose: a missing flag must stay distinguishable from {@code false}. */
        private Boolean approvalEnabled;
        private String enabledByName;
        private String enabledOn;
    }
}
