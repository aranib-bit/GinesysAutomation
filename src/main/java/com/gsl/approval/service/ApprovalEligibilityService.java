package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.model.ApprovalRequest;

/**
 * Phase 1 decision: is approval enabled for the request's entity? Only an explicit, valid {@code approvalEnabled} flag on the right entity
 * counts; an empty answer, a missing flag, another entity's row or any API failure is an {@link ApprovalConfigurationException}, never
 * "disabled".
 */
public class ApprovalEligibilityService {

    private final ApprovalConfigurationGateway gateway;

    public ApprovalEligibilityService(ApprovalConfigurationGateway gateway) {
        this.gateway = gateway;
    }

    /** The validated configuration of the request's entity. */
    public ApprovalEntity getEntityConfiguration(ApprovalRequest request) {
        validate(request);
        ApprovalEntity entity = gateway.fetchEntityConfiguration(request.getEntityCode());
        if (entity == null) {
            throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.NOT_FOUND,
                    "No approval configuration row for entity '" + request.getEntityCode() + "'");
        }
        if (!request.getEntityCode().equalsIgnoreCase(entity.getEntityCode())) {
            throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.ENTITY_MISMATCH,
                    "Requested entity '" + request.getEntityCode() + "' but the configuration is for '" + entity.getEntityCode() + "'");
        }
        if (entity.getApprovalEnabled() == null) {
            throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.INVALID_RESPONSE,
                    "Approval-enabled flag is missing for entity '" + request.getEntityCode() + "'");
        }
        return entity;
    }

    /** {@code true} only when the configuration explicitly says enabled. */
    public boolean isApprovalEnabled(ApprovalRequest request) {
        return Boolean.TRUE.equals(getEntityConfiguration(request).getApprovalEnabled());
    }

    private static void validate(ApprovalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ApprovalRequest must not be null");
        }
        if (request.getEntityCode() == null || request.getEntityCode().isBlank()) {
            throw new IllegalArgumentException("Approval entity code is required");
        }
    }
}
