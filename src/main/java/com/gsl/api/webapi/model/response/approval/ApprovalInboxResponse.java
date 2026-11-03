package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code POST ApprovalInbox/GetPending} and {@code GetArchive}: a page of the approver's tasks with the grid's total. */
@Getter
@Setter
public class ApprovalInboxResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer totalCount;
        private List<Item> items;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Integer taskCode;
        private Integer instanceCode;
        private String entityCode;
        /** The document's own code (the voucher code for AP). */
        private String refCode;
        /** The document number shown to users. */
        private String schemeDocNo;
        private Integer levelNo;
        private Integer totalLevels;
        private Boolean isFinalLevel;
        private Integer approvedCount;
        private Integer quorumReq;
        private String waitingOnDisplay;
        /** Archive rows: what this approver did, e.g. {@code APPROVED}. */
        private String actionTaken;
        /** Archive rows: the whole request's state, e.g. {@code INPROGRESS} or {@code APPROVED}. */
        private String instanceStatus;
    }
}
