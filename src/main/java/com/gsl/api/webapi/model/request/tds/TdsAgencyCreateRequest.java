package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST tdsagency} body, as the UI records it; only the id, name, class, ledger and user vary. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsAgencyCreateRequest {
    @JsonProperty("code")
    private int code;
    @JsonProperty("agencyId")
    private String agencyId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("alias")
    private String alias;
    @JsonProperty("clscode")
    private long classCode;
    @JsonProperty("classType")
    private String classType;
    @JsonProperty("glcode")
    private long glCode;
    @JsonProperty("remarks")
    private String remarks;
    @JsonProperty("address")
    private String address;
    @JsonProperty("cityCode")
    private Integer cityCode;
    @JsonProperty("city")
    private String city;
    @JsonProperty("district")
    private String district;
    @JsonProperty("state")
    private String state;
    @JsonProperty("std")
    private String std;
    @JsonProperty("country")
    private String country;
    @JsonProperty("pin")
    private String pin;
    @JsonProperty("phone1")
    private String phone1;
    @JsonProperty("phone2")
    private String phone2;
    @JsonProperty("phone3")
    private String phone3;
    @JsonProperty("residenceNO")
    private String residenceNo;
    @JsonProperty("email")
    private String email;
    @JsonProperty("email2")
    private String email2;
    @JsonProperty("fax")
    private String fax;
    @JsonProperty("mobile")
    private String mobile;
    @JsonProperty("contactName")
    private String contactName;
    @JsonProperty("website")
    private String website;
    @JsonProperty("bankName")
    private String bankName;
    @JsonProperty("accountNo")
    private String accountNo;
    @JsonProperty("micrCode")
    private String micrCode;
    @JsonProperty("ifsCode")
    private String ifsCode;
    @JsonProperty("rtgsCode")
    private String rtgsCode;
    @JsonProperty("chequeLabel")
    private String chequeLabel;
    @JsonProperty("connectedUser")
    private long connectedUser;
    @JsonProperty("dataversion")
    private int dataVersion;
    @JsonProperty("extinct")
    private String extinct;
    @JsonProperty("udfValues")
    private List<AgencyUdfValue> udfValues;
}
