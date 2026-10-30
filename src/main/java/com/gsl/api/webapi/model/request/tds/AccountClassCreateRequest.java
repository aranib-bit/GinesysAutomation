package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account class as the Account Class screen posts it ({@code AccountClass/Update} was recorded; the create sends the same record with
 * {@code code=0}). {@code clstype} D is the TDS Agency type; {@code id} is the screen's ExtJS model id.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AccountClassCreateRequest {
    @JsonProperty("clsname")
    private String className;
    @JsonProperty("ext")
    private String ext;
    @JsonProperty("clstype")
    private String classType;
    @JsonProperty("cmpcode")
    private int companyCode;
    @JsonProperty("code")
    private int code;
    @JsonProperty("id")
    private String id;
}
