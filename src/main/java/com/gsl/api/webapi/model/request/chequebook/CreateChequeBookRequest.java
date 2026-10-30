package com.gsl.api.webapi.model.request.chequebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Body of {@code POST /cheques/create}. Built by {@code ChequeBookRequestFactory}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CreateChequeBookRequest {
    private int code;
    private long ownerSiteCode;
    private String dateOfIssue;
    private String remarks;
    private long empCode;
    private long glcode;
    private int dataversion;
    private List<ChequeDetailRequest> chequeDetails;
}
