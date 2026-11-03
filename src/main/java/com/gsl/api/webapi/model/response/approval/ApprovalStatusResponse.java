package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

/** Response of {@code GET ApprovalAction/GetStatus}: the document's approval state ({@code PENDING}, {@code APPROVED}, ...). */
@Getter
@Setter
public class ApprovalStatusResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String status;
        private String label;
        private Integer instanceCode;
        private Integer currentLevelNo;
    }
}
