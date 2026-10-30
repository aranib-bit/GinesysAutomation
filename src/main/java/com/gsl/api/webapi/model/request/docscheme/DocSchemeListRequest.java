package com.gsl.api.webapi.model.request.docscheme;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST MenuDocScheme/GetListOfDocScheme} body. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DocSchemeListRequest {
    @JsonProperty("pagingNotRequired")
    private boolean pagingNotRequired;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
}
