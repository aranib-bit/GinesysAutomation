package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One user-defined field of an agency; the UI sends every configured field, empty unless the configuration makes it mandatory. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AgencyUdfValue {
    @JsonProperty("fieldName")
    private String fieldName;
    @JsonProperty("value")
    private String value;
    @JsonProperty("datatype")
    private String datatype;
}
