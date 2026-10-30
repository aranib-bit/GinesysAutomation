package com.gsl.api.webapi.model.response.party;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** One row of {@code VendorMaster/GetVendors} / {@code Customer/GetCustomers}; {@code code} is the sub-ledger code. Unknown columns are ignored. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartyListItem {
    @JsonProperty("code")
    private Long code;
    @JsonProperty("slid")
    private String slId;
    @JsonProperty("slname")
    private String name;
    /** The class NAME (the row carries no class code). */
    @JsonProperty("slclass")
    private String className;
    @JsonProperty("slclasstype")
    private String classType;
    /** "Yes" / "No". */
    @JsonProperty("isextinct")
    private String extinct;
    /** Vendor rows only: "Yes" / "No". */
    @JsonProperty("tdsapplicable")
    private String tdsApplicable;

    public boolean isExtinct() {
        return "yes".equalsIgnoreCase(extinct);
    }

    public boolean isTdsApplicable() {
        return "yes".equalsIgnoreCase(tdsApplicable);
    }
}
