package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code IsLedgerTaggedInFINGL_SITE}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LedgerTaggedQuery {
    @JsonProperty("_dc")
    private long cacheBuster;
    @JsonProperty("glCode")
    private int glCode;
    @JsonProperty("ownerSiteCode")
    private int ownerSiteCode;
}
