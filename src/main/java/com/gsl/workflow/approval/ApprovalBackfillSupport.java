package com.gsl.workflow.approval;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.DataSetupException;
import com.gsl.approval.auth.ApproverSessionService;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalStatus;
import com.gsl.approval.model.ApprovalWorkflowResult;
import com.gsl.approval.reporting.ExtentApprovalReporter;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;

/**
 * The one call a data-setup (backfill) strategy makes after it created a document: approve it through the approval workflow when
 * {@code approval.autoapprove.enabled} is on and the entity has approval enabled. Off, or approval disabled for the entity: nothing happens.
 * Anything that leaves the document unapproved or unposted is a {@link DataSetupException} saying why, so a backfill never hands a test a
 * document that cannot be used and silently skips.
 */
public final class ApprovalBackfillSupport {

    private ApprovalBackfillSupport() {}

    public static boolean enabled() {
        return "yes".equalsIgnoreCase(ConfigFactory.getApiConfig().approvalAutoApproveEnabled().trim());
    }

    /**
     * Approves an AP Voucher a backfill just created. The rule facts are what the backfill knows about the voucher (amount, site, creator,
     * date, number, sub-ledger); a rule that needs a value not listed here ends as {@code CONFIGURATION_ERROR} naming the variable.
     */
    public static ApprovalWorkflowResult approveApVoucher(ApiSessionBundle owner, com.gsl.api.model.apvoucher.ApVoucherCreateResponse voucher,
                                                          long subLedgerCode, int siteCode, int ouCode, long empCode, java.math.BigDecimal amount) {
        if (!enabled()) {
            return null;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        return approveAfterCreate(owner, siteCode, ouCode, ApprovalRequest.builder()
                .entityCode(com.gsl.approval.model.ApprovalEntityType.AP.code())
                .documentCode(voucher.getVoucherCode())
                .documentNumber(voucher.getDocScheme())
                .amount(amount)
                .facts(com.gsl.approval.facts.ApVoucherApprovalFacts.builder()
                        .totalAmount(amount)
                        .ownerSite(siteCode)
                        .createdPerson((int) empCode)
                        .voucherDate(today)
                        .createdOn(today)
                        .voucherNo(voucher.getDocScheme())
                        .subLedgers(java.util.List.of((int) subLedgerCode))
                        .build())
                .build());
    }

    /** @return the result, or {@code null} when the feature is off */
    public static ApprovalWorkflowResult approveAfterCreate(ApiSessionBundle owner, int siteCode, int ouCode, ApprovalRequest request) {
        if (!enabled()) {
            return null;
        }
        ApproverSessionService sessions = ApprovalWorkflows.apiSessions();
        try {
            ApprovalWorkflow workflow = ApprovalWorkflows.overApi(owner, siteCode, ouCode,
                    new ExtentApprovalReporter("Approval Workflow [" + request.getEntityCode() + "] " + request.getDocumentNumber()), sessions);
            ApprovalWorkflowResult result = workflow.execute(request);
            ReportLogger.dataBackfill("[Approval] {} {} -> {} ({}){}", request.getEntityCode(), request.getDocumentNumber(), result.getStatus(),
                    result.getMessage(), result.getPostedState() == null ? "" : ", " + result.getPostedState());
            if (result.getStatus() != ApprovalStatus.SKIPPED && !result.isCompleted()) {
                throw new DataSetupException("[Approval] " + request.getDocumentNumber() + " was not approved and posted: " + result.getStatus()
                        + " - " + result.getMessage());
            }
            return result;
        } catch (DataSetupException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DataSetupException("[Approval] Approving " + request.getDocumentNumber() + " failed: " + e.getMessage(), e);
        } finally {
            sessions.releaseAll();
        }
    }
}
