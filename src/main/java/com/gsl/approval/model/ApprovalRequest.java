package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One submitted document to run the approval workflow for. Built by the calling flow after the document was saved successfully; carries only
 * what the approval services need. {@code facts} (rule variable code to value) is used from Phase 3 on.
 */
@Value
@Builder(toBuilder = true)
public class ApprovalRequest {
    /** Entity code as the approval APIs spell it, e.g. {@code AP}. */
    String entityCode;
    /** The document's internal code (the inbox's {@code refCode}), e.g. the voucher code. */
    String documentCode;
    /** The document number shown to users (the inbox's {@code schemeDocNo}). */
    String documentNumber;
    BigDecimal amount;
    Map<String, Object> facts;

    public ApprovalEntityType entityType() {
        return ApprovalEntityType.fromCode(entityCode);
    }
}
