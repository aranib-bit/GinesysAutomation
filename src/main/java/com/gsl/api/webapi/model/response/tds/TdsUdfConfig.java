package com.gsl.api.webapi.model.response.tds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** One user-defined field of {@code GET udfconfigurations} (only what building the agency's {@code udfValues} needs). */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsUdfConfig {
    private String columnName;
    private String dataType;
    @JsonProperty("isMandatory")
    private Boolean mandatory;
    private String defaultValue;

    public boolean isRequired() {
        return Boolean.TRUE.equals(mandatory);
    }
}
