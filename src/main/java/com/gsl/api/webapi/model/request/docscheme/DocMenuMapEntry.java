package com.gsl.api.webapi.model.request.docscheme;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One entry of {@code POST MenuDocScheme/SaveDocMenuMap}: tags ({@code onlyDelete=false}) or untags ({@code true}) one menu for a scheme. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DocMenuMapEntry {
    @JsonProperty("docCode")
    private long docCode;
    @JsonProperty("menuCode")
    private Integer menuCode;
    @JsonProperty("menuShCode")
    private String menuShCode;
    @JsonProperty("onlyDelete")
    private boolean onlyDelete;
}
