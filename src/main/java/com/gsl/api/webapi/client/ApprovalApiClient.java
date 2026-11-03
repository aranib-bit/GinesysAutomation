package com.gsl.api.webapi.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.approval.ApprovalEntityConfigsRequest;
import com.gsl.api.webapi.model.request.approval.ApprovalEntityQuery;
import com.gsl.api.webapi.model.request.approval.ApprovalDocumentQuery;
import com.gsl.api.webapi.model.request.approval.ApprovalIdQuery;
import com.gsl.api.webapi.model.request.approval.ApprovalInboxRequest;
import com.gsl.api.webapi.model.request.approval.ApproveRequest;
import com.gsl.api.webapi.model.response.approval.ApprovalInboxResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalPathResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalStatusResponse;
import com.gsl.api.webapi.model.request.approval.GlobalUserListRequest;
import com.gsl.api.webapi.model.response.approval.GlobalUserListResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalActiveRuleSetResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalEntityConfigsResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalHierarchyResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalRuleDetailResponse;
import com.gsl.api.webapi.model.response.common.BooleanResultResponse;
import io.restassured.specification.RequestSpecification;

import java.util.List;

/**
 * Approval workflow HTTP calls only (HARs: Entities, APApprovalEnable). One method per endpoint; deciding what a response means (for
 * example that a missing entity is a failure, not "disabled") is {@code ApprovalEligibilityService}'s job. Only the gateway classes in
 * {@code com.gsl.approval.api} and the executors may use this client.
 */
public class ApprovalApiClient extends ApiClient {

    /** First page size for the entity list; the list has a handful of rows (one per approval-capable entity). */
    private static final int ENTITY_PAGE_LIMIT = 25;

    /** Wraps an already-authenticated session {@code spec} - see {@link ApiClient}'s class Javadoc. */
    public ApprovalApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** {@code POST ApprovalEntityConfig/GetEntityConfigs}: every entity row with its {@code approvalEnabled} flag (null list when the body had none). */
    public List<ApprovalEntityConfigsResponse.Item> getEntityConfigs() {
        return post(WebApiEndpoints.Approval.ENTITY_CONFIGS, ApprovalEntityConfigsRequest.firstPage(ENTITY_PAGE_LIMIT),
                ApprovalEntityConfigsResponse.class).getResult();
    }

    /** {@code GET ApprovalRulePriority/GetActiveVersion?entityCode=}: the active priority set (null when the body had no result). */
    public ApprovalActiveRuleSetResponse.Result getActiveRuleSet(String entityCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.ACTIVE_RULE_SET, ApprovalEntityQuery.of(entityCode)),
                ApprovalActiveRuleSetResponse.class).getResult();
    }

    /** {@code GET ApprovalRule/Get?id=}: one rule with its hierarchy code and conditions (null when the body had no result). */
    public ApprovalRuleDetailResponse.Result getRule(int ruleCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.RULE_GET, ApprovalIdQuery.of(ruleCode)),
                ApprovalRuleDetailResponse.class).getResult();
    }

    /** {@code GET ApprovalHierarchy/Get?id=}: one hierarchy with its levels and approvers (null when the body had no result). */
    public ApprovalHierarchyResponse.Result getHierarchy(int hierarchyCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.HIERARCHY_GET, ApprovalIdQuery.of(hierarchyCode)),
                ApprovalHierarchyResponse.class).getResult();
    }

    /** {@code POST ApprovalInbox/GetPending}: one page of the logged-in approver's pending tasks (null when the body had no result). */
    public ApprovalInboxResponse.Result getPending(String entityCode, int page, int limit) {
        return post(WebApiEndpoints.Approval.INBOX_PENDING, ApprovalInboxRequest.page(entityCode, page, limit), ApprovalInboxResponse.class).getResult();
    }

    /** {@code POST ApprovalInbox/GetArchive}: one page of the logged-in approver's finished tasks (null when the body had no result). */
    public ApprovalInboxResponse.Result getArchive(String entityCode, int page, int limit) {
        return post(WebApiEndpoints.Approval.INBOX_ARCHIVE, ApprovalInboxRequest.page(entityCode, page, limit), ApprovalInboxResponse.class).getResult();
    }

    /** {@code POST ApprovalAction/Approve}: approves {@code taskCode} as the logged-in approver; {@code true} when the application accepted it. */
    public boolean approve(int taskCode, String remarks) {
        return post(WebApiEndpoints.Approval.APPROVE, ApproveRequest.of(taskCode, remarks), BooleanResultResponse.class).isTrue();
    }

    /** {@code GET ApprovalAction/GetApprovalPath?entityCode=&refCode=}: the document's approval path (null when the body had no result). */
    public ApprovalPathResponse.Result getApprovalPath(String entityCode, String refCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.APPROVAL_PATH, ApprovalDocumentQuery.of(entityCode, refCode)), ApprovalPathResponse.class).getResult();
    }

    /** {@code GET ApprovalAction/GetStatus?entityCode=&refCode=}: the document's approval status (null when the body had no result). */
    public ApprovalStatusResponse.Result getStatus(String entityCode, String refCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.STATUS, ApprovalDocumentQuery.of(entityCode, refCode)), ApprovalStatusResponse.class).getResult();
    }

    /** {@code POST GlobalUser/GetListOfGlobalAppEmpUsers}: one page of the user directory (null when the body had no result). */
    public GlobalUserListResponse.Result getUserPage(GlobalUserListRequest request) {
        return post(WebApiEndpoints.GlobalUser.LIST, request, GlobalUserListResponse.class).getResult();
    }

    /** {@code GET ApprovalAction/IsWorkflowEnabled?entityCode=}: the same switch as seen by the document screens. */
    public boolean isWorkflowEnabled(String entityCode) {
        return bodyAs(getRawQuery(WebApiEndpoints.Approval.IS_WORKFLOW_ENABLED, ApprovalEntityQuery.of(entityCode)),
                BooleanResultResponse.class).isTrue();
    }
}
