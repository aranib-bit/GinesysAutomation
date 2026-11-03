package com.gsl.approval.service;

import com.gsl.approval.auth.ApprovalAuthenticationException;
import com.gsl.approval.auth.ApproverSession;
import com.gsl.approval.auth.ApproverSessionService;
import com.gsl.approval.executor.ApprovalActionException;
import com.gsl.approval.executor.ApprovalExecutionStrategy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApprovalPath;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalTask;
import com.gsl.approval.model.ApproverIdentity;
import com.gsl.approval.reporting.ApprovalReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Works one hierarchy level through to its quorum: finds the document's task in an approver's inbox, approves it as that approver, and
 * repeats with the next approver until the level is satisfied. The application's own approval path (when the strategy can read it) is the
 * authority on "satisfied" and on how many approvals the level already had; without it the approvals made here are counted against the
 * hierarchy's quorum. Approvals are never blind-retried: a stale reused session is replaced by a fresh login and tried once more, and an
 * approval whose outcome is unknown (no answer) is reconciled against the inbox before anything is repeated.
 */
public class ApprovalLevelService {

    private static final String REMARKS = "Approved by automation";

    private final ApprovalAssignmentService assignments;
    private final ApproverSessionService sessions;
    private final ApprovalExecutionStrategy executor;
    private final ApprovalPoller poller;
    private final ApprovalReporter reporter;

    public ApprovalLevelService(ApprovalAssignmentService assignments, ApproverSessionService sessions, ApprovalExecutionStrategy executor,
                                ApprovalPoller poller, ApprovalReporter reporter) {
        this.assignments = assignments;
        this.sessions = sessions;
        this.executor = executor;
        this.poller = poller;
        this.reporter = reporter;
    }

    /** {@link #processLevel(ApprovalRequest, ApprovalLevel, String, String)} without a cross-check of the application's rule and hierarchy. */
    public List<String> processLevel(ApprovalRequest request, ApprovalLevel level) {
        return processLevel(request, level, null, null);
    }

    /**
     * @param expectedRule      the rule the framework resolved, to compare with the application's (null = do not compare)
     * @param expectedHierarchy the hierarchy the framework loaded, to compare with the application's (null = do not compare)
     * @return the login names of the users who approved at this level, in order
     */
    public List<String> processLevel(ApprovalRequest request, ApprovalLevel level, String expectedRule, String expectedHierarchy) {
        List<ApproverIdentity> remaining = new ArrayList<>(assignments.resolveApprovers(level));
        List<String> approvedBy = new ArrayList<>();
        int needed = level.requiredApprovals();
        int baseline = 0;                 // approvals the level had before this workflow added any
        boolean baselineRead = false;
        boolean pathUsable = false;
        boolean firstLookup = true;

        while (true) {
            if (remaining.isEmpty()) {
                throw new ApprovalIncompleteException(approvedBy.isEmpty() && !baselineRead
                        ? "Level " + level.getLevelNo() + ": no approval task for " + request.getDocumentNumber()
                        + " ever appeared in an approver's inbox - the document was probably never sent for approval"
                        : "Level " + level.getLevelNo() + " of " + request.getDocumentNumber() + " has " + (baseline + approvedBy.size()) + " of "
                        + needed + " approval(s) and no approver left to ask");
            }
            ApproverSession session = acquire(request, level, remaining, baselineRead, firstLookup);
            ApproverIdentity who = session.identity();
            remaining.removeIf(a -> a.getUserCode() == who.getUserCode());

            // The application's own view first: does the request exist, and is this level already done?
            Optional<ApprovalPath.Level> pathLevel = readPathLevel(session, request, level, firstLookup, expectedRule, expectedHierarchy);
            if (pathLevel.isPresent()) {
                pathUsable = true;
                if (!baselineRead) {
                    baseline = pathLevel.get().getApprovedCount();
                    baselineRead = true;
                    if (pathLevel.get().getQuorumRequired() > 0) {
                        needed = pathLevel.get().getQuorumRequired();
                    }
                }
                if (pathLevel.get().isSatisfied()) {
                    break;
                }
            }

            // The task appears a moment after the previous level's last approval, so the first lookup waits; later ones are single checks.
            boolean waitForIt = firstLookup;
            Optional<ApprovalTask> found = waitForIt ? poller.until(() -> findTask(session, request, level)) : findTask(session, request, level);
            firstLookup = false;
            if (found.isEmpty()) {
                reporter.info("Level " + level.getLevelNo() + ": no task for " + request.getDocumentNumber() + " in the inbox of '" + who.getUsername() + "'");
                continue;
            }
            ApprovalTask task = found.get();
            if (!pathUsable && !baselineRead) {
                // No path to read: take the task's own count as the baseline (later reads also count our approvals, so only the first one is a baseline).
                baseline = task.getApprovedCount();
                baselineRead = true;
            }
            if (!pathUsable && baseline + approvedBy.size() >= needed) {
                break;
            }
            reporter.info("Level " + level.getLevelNo() + ": approving task " + task.getTaskCode() + " as '" + who.getUsername() + "' ("
                    + (session.reused() ? "live session reused" : "logged in") + ")");
            approve(session, task);
            approvedBy.add(who.getUsername());

            if (pathUsable) {
                int target = baseline + approvedBy.size();
                Optional<ApprovalPath.Level> after = poller.until(() -> executor.path(session, request.getEntityCode(), request.getDocumentCode())
                        .flatMap(p -> p.level(level.getLevelNo()))
                        .filter(l -> l.isSatisfied() || l.getApprovedCount() >= target));
                if (after.isEmpty()) {
                    throw new ApprovalIncompleteException("Level " + level.getLevelNo() + " of " + request.getDocumentNumber()
                            + ": the application did not register the approval of '" + who.getUsername() + "'");
                }
                if (after.get().isSatisfied()) {
                    break;
                }
            } else if (baseline + approvedBy.size() >= needed) {
                break;
            }
        }
        return approvedBy;
    }

    private ApproverSession acquire(ApprovalRequest request, ApprovalLevel level, List<ApproverIdentity> remaining, boolean baselineRead, boolean firstLookup) {
        try {
            return sessions.acquireFirstAvailable(remaining);
        } catch (ApprovalAuthenticationException e) {
            if (!baselineRead && !firstLookup) {
                // An approver already looked and found nothing, and nobody else can be asked: the real problem is the missing task.
                throw new ApprovalIncompleteException("Level " + level.getLevelNo() + ": no approval task for " + request.getDocumentNumber()
                        + " appeared in the inbox of the approver(s) who could be asked (" + e.getMessage()
                        + ") - the document was probably never sent for approval");
            }
            throw e;
        }
    }

    /**
     * This level's state in the application's approval path. The first read waits for the approval request to exist (a save sends the
     * document for approval a moment before the path shows it) and compares the application's rule and hierarchy with the framework's.
     */
    private Optional<ApprovalPath.Level> readPathLevel(ApproverSession session, ApprovalRequest request, ApprovalLevel level, boolean first,
                                                       String expectedRule, String expectedHierarchy) {
        java.util.function.Supplier<Optional<ApprovalPath>> read = () -> executor.path(session, request.getEntityCode(), request.getDocumentCode())
                .filter(p -> p.level(level.getLevelNo()).isPresent());
        Optional<ApprovalPath> path = first ? poller.until(read) : read.get();
        if (path.isEmpty()) {
            return Optional.empty();
        }
        if (first) {
            crossCheck(path.get(), expectedRule, expectedHierarchy);
        }
        return path.get().level(level.getLevelNo());
    }

    private void crossCheck(ApprovalPath path, String expectedRule, String expectedHierarchy) {
        boolean ruleDiffers = expectedRule != null && path.getRuleName() != null && !expectedRule.equalsIgnoreCase(path.getRuleName());
        boolean hierarchyDiffers = expectedHierarchy != null && path.getHierarchyName() != null && !expectedHierarchy.equalsIgnoreCase(path.getHierarchyName());
        if (ruleDiffers || hierarchyDiffers) {
            reporter.info("MISMATCH with the application: it applied rule '" + path.getRuleName() + "' / hierarchy '" + path.getHierarchyName()
                    + "' but the framework resolved rule '" + expectedRule + "' / hierarchy '" + expectedHierarchy + "'");
        } else {
            reporter.info("The application applied the same rule ('" + path.getRuleName() + "') and hierarchy ('" + path.getHierarchyName() + "')");
        }
    }

    private Optional<ApprovalTask> findTask(ApproverSession session, ApprovalRequest request, ApprovalLevel level) {
        return executor.pending(session, request.getEntityCode()).stream()
                .filter(t -> t.isFor(request) && t.getLevelNo() == level.getLevelNo())
                .findFirst();
    }

    private void approve(ApproverSession session, ApprovalTask task) {
        try {
            executor.approve(session, task, REMARKS);
        } catch (ApprovalActionException e) {
            switch (e.getKind()) {
                case STALE_SESSION -> {
                    if (!session.reused()) {
                        throw e;
                    }
                    reporter.info("The reused session of '" + session.identity().getUsername() + "' is no longer valid - logging in instead");
                    executor.approve(sessions.acquireFresh(session.identity()), task, REMARKS);
                }
                case AMBIGUOUS -> {
                    // The action may have been applied: only accept that if the task has left this approver's inbox; never approve twice.
                    boolean stillWaiting = executor.pending(session, task.getEntityCode()).stream().anyMatch(t -> t.getTaskCode() == task.getTaskCode());
                    if (stillWaiting) {
                        throw e;
                    }
                    reporter.info("Approval of task " + task.getTaskCode() + " got no answer but the task has left the inbox - counted as approved");
                }
                default -> throw e;
            }
        }
    }
}
