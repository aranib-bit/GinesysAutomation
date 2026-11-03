package com.gsl.approval.api;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.response.approval.ApprovalEntityConfigsResponse;
import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.service.ApprovalConfigurationException;
import com.gsl.approval.service.ApprovalConfigurationGateway;

import java.util.List;

/** {@link ApprovalConfigurationGateway} over {@link ApprovalApiClient}: maps an HTTP failure to its {@link ApprovalConfigurationException.Kind}. */
public class ApiApprovalConfigurationGateway implements ApprovalConfigurationGateway {

    private final ApprovalApiClient client;

    public ApiApprovalConfigurationGateway(ApprovalApiClient client) {
        this.client = client;
    }

    @Override
    public ApprovalEntity fetchEntityConfiguration(String entityCode) {
        List<ApprovalEntityConfigsResponse.Item> items =
                ApprovalApiCalls.read("Reading approval entity configuration", client::getEntityConfigs);
        if (items == null) {
            throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.INVALID_RESPONSE,
                    "Approval entity configuration response had no result list");
        }
        return items.stream()
                .filter(i -> entityCode.equalsIgnoreCase(i.getEntityCode()))
                .findFirst()
                .map(i -> ApprovalEntity.builder()
                        .entityCode(i.getEntityCode())
                        .displayName(i.getDisplayName())
                        .approvalEnabled(i.getApprovalEnabled())
                        .enabledByName(i.getEnabledByName())
                        .enabledOn(i.getEnabledOn())
                        .build())
                .orElse(null);
    }
}
