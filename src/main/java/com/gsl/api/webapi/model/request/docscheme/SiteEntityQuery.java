package com.gsl.api.webapi.model.request.docscheme;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code GetOrganizationSite} and {@code AdmOrganizationSite/Update}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiteEntityQuery {
    @JsonProperty("code")
    private long code;
}
