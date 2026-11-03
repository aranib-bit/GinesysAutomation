package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalEntity;

/** Reads one entity's approval configuration. The only way the eligibility service reaches the application, so it can be faked in tests. */
public interface ApprovalConfigurationGateway {

    /**
     * @return the entity's configuration, or {@code null} when the application's list has no row for it
     * @throws ApprovalConfigurationException when the call itself failed (authentication, authorization, server, transport)
     */
    ApprovalEntity fetchEntityConfiguration(String entityCode);
}
