package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

/** The approval configuration of one entity as the application reports it. */
@Value
@Builder
public class ApprovalEntity {
    String entityCode;
    String displayName;
    /** {@code null} only for a malformed response; the service rejects that, it is never read as "disabled". */
    Boolean approvalEnabled;
    String enabledByName;
    String enabledOn;
}
