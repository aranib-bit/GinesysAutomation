package com.gsl.workflow.approval;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.approval.api.ApiApprovalConfigurationGateway;
import com.gsl.approval.api.ApiApprovalPolicyGateway;
import com.gsl.approval.api.ApiUserDirectoryGateway;
import com.gsl.approval.auth.ApiLiveSessions;
import com.gsl.approval.auth.ApiLoginStrategy;
import com.gsl.approval.auth.ApproverSessionService;
import com.gsl.approval.auth.BrowserLiveSessions;
import com.gsl.approval.auth.UsersListCredentialSource;
import com.gsl.approval.evaluator.ApprovalConditionEvaluator;
import com.gsl.approval.evaluator.ApprovalRuleResolver;
import com.gsl.approval.executor.ApiApprovalStrategy;
import com.gsl.approval.reporting.ApprovalReporter;
import com.gsl.approval.service.ApprovalAssignmentService;
import com.gsl.approval.service.ApprovalEligibilityService;
import com.gsl.approval.service.ApprovalHierarchyService;
import com.gsl.approval.service.ApprovalLevelService;
import com.gsl.approval.service.ApprovalRuleService;
import com.gsl.approval.service.UserDirectory;
import com.gsl.approval.service.WaitConfigPoller;
import com.gsl.approval.validation.ApprovalPostedValidator;
import com.gsl.approval.validation.FinPostStateSource;
import com.gsl.config.ConfigFactory;

import java.time.LocalDate;

/** Builds the complete {@link ApprovalWorkflow} over the WEBAPI: the document owner's session reads the configuration, approvers act on their own sessions. */
public final class ApprovalWorkflows {

    private ApprovalWorkflows() {}

    /**
     * @param owner         the session of the user who saved the document (reads the approval configuration and the user directory)
     * @param siteCode      the site the document belongs to (the directory is read in that site's context)
     * @param ouCode        the organization unit of that site
     * @param reporter      where the steps are reported
     * @param sessions      approver sessions; the caller releases them when the workflow is done
     */
    public static ApprovalWorkflow overApi(ApiSessionBundle owner, int siteCode, int ouCode, ApprovalReporter reporter, ApproverSessionService sessions) {
        ApprovalApiClient client = new ApprovalApiClient(owner.getBaseSpec());
        int pageSize = ConfigFactory.getApiConfig().approvalPageSize();
        ApiApprovalPolicyGateway policy = new ApiApprovalPolicyGateway(client);
        UserDirectory directory = new UserDirectory(
                new ApiUserDirectoryGateway(client, String.valueOf(siteCode), ouCode, LocalDate.now() + "T00:00:00"), pageSize);
        WaitConfigPoller poller = new WaitConfigPoller();
        ApprovalLevelService levels = new ApprovalLevelService(new ApprovalAssignmentService(directory), sessions,
                new ApiApprovalStrategy(pageSize), poller, reporter);
        ApprovalOrchestrator orchestrator = new ApprovalOrchestrator(new ApprovalRuleService(policy),
                new ApprovalRuleResolver(new ApprovalConditionEvaluator()), new ApprovalHierarchyService(policy), levels,
                new ApprovalPostedValidator(new FinPostStateSource(), poller), reporter, LocalDate::now);
        return new ApprovalWorkflow(new ApprovalEligibilityService(new ApiApprovalConfigurationGateway(client)), orchestrator, reporter);
    }

    /** The approver session service every API workflow uses: live browser tokens, live API sessions, then a login from {@code UsersList.xlsx}. */
    public static ApproverSessionService apiSessions() {
        return new ApproverSessionService(java.util.List.of(new BrowserLiveSessions(), new ApiLiveSessions()),
                new UsersListCredentialSource(), new ApiLoginStrategy());
    }
}
