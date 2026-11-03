package com.gsl.approval.executor;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.response.approval.ApprovalInboxResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalPathResponse;
import com.gsl.approval.auth.ApproverSession;
import com.gsl.approval.model.ApprovalPath;
import com.gsl.approval.model.ApprovalTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** {@link ApprovalExecutionStrategy} over the WEBAPI, building an {@link ApprovalApiClient} on each approver session's own authenticated spec. */
public class ApiApprovalStrategy implements ApprovalExecutionStrategy {

    /** Safety cap so a server that keeps returning rows cannot loop forever. */
    private static final int MAX_PAGES = 200;

    private final int pageSize;
    private final Function<ApproverSession, ApprovalApiClient> clientFor;

    public ApiApprovalStrategy(int pageSize) {
        this(pageSize, s -> new ApprovalApiClient(s.spec()));
    }

    /** For tests: supply the client each session maps to. */
    public ApiApprovalStrategy(int pageSize, Function<ApproverSession, ApprovalApiClient> clientFor) {
        this.pageSize = pageSize;
        this.clientFor = clientFor;
    }

    @Override
    public List<ApprovalTask> pending(ApproverSession session, String entityCode) {
        ApprovalApiClient client = clientFor.apply(session);
        return readAllPages(entityCode, (code, page) -> client.getPending(code, page, pageSize), session, "pending tasks");
    }

    @Override
    public List<ApprovalTask> archive(ApproverSession session, String entityCode) {
        ApprovalApiClient client = clientFor.apply(session);
        return readAllPages(entityCode, (code, page) -> client.getArchive(code, page, pageSize), session, "archived tasks");
    }

    @Override
    public Optional<ApprovalPath> path(ApproverSession session, String entityCode, String documentCode) {
        ApprovalPathResponse.Result r;
        try {
            r = clientFor.apply(session).getApprovalPath(entityCode, documentCode);
        } catch (RuntimeException e) {
            // Advisory view: no approval request yet answers with an error, and the caller falls back to counting - it is not a failure here.
            return Optional.empty();
        }
        if (r == null || r.getLevels() == null) {
            return Optional.empty();
        }
        List<ApprovalPath.Level> levels = r.getLevels().stream()
                .map(l -> ApprovalPath.Level.builder().levelNo(l.getLevelNo() == null ? 0 : l.getLevelNo())
                        .quorumRequired(l.getQuorumReq() == null ? 0 : l.getQuorumReq())
                        .approvedCount(l.getApprovedCount() == null ? 0 : l.getApprovedCount())
                        .status(l.getStatus()).build())
                .sorted(Comparator.comparingInt(ApprovalPath.Level::getLevelNo))
                .toList();
        return Optional.of(ApprovalPath.builder().status(r.getStatus()).ruleName(r.getRuleName()).hierarchyName(r.getHierarchyName()).levels(levels).build());
    }

    @Override
    public void approve(ApproverSession session, ApprovalTask task, String remarks) {
        boolean accepted;
        try {
            accepted = clientFor.apply(session).approve(task.getTaskCode(), remarks);
        } catch (ApiException e) {
            throw map(e, "Approving task " + task.getTaskCode());
        } catch (RuntimeException e) {
            throw new ApprovalActionException(ApprovalActionException.Kind.AMBIGUOUS, "Approving task " + task.getTaskCode() + " got no answer: " + e.getMessage(), e);
        }
        if (!accepted) {
            throw new ApprovalActionException(ApprovalActionException.Kind.REJECTED,
                    "The application did not accept approval of task " + task.getTaskCode() + " as '" + session.identity().getUsername() + "'");
        }
    }

    @FunctionalInterface
    private interface PageReader {
        ApprovalInboxResponse.Result read(String entityCode, int page);
    }

    private List<ApprovalTask> readAllPages(String entityCode, PageReader reader, ApproverSession session, String what) {
        List<ApprovalTask> tasks = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        try {
            for (int page = 1; page <= MAX_PAGES && tasks.size() < total; page++) {
                ApprovalInboxResponse.Result result = reader.read(entityCode, page);
                if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
                    break;
                }
                total = result.getTotalCount() == null ? result.getItems().size() : result.getTotalCount();
                result.getItems().forEach(i -> tasks.add(toTask(i)));
            }
        } catch (ApiException e) {
            throw map(e, "Reading " + what + " of '" + session.identity().getUsername() + "'");
        } catch (RuntimeException e) {
            throw new ApprovalActionException(ApprovalActionException.Kind.AMBIGUOUS, "Reading " + what + " got no answer: " + e.getMessage(), e);
        }
        return tasks;
    }

    private static ApprovalActionException map(ApiException e, String what) {
        ApprovalActionException.Kind kind = e.getStatusCode() == 401 ? ApprovalActionException.Kind.STALE_SESSION
                : e.getStatusCode() == 0 || e.getStatusCode() >= 500 ? ApprovalActionException.Kind.AMBIGUOUS
                : ApprovalActionException.Kind.REJECTED;
        return new ApprovalActionException(kind, what + " failed (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
    }

    private static ApprovalTask toTask(ApprovalInboxResponse.Item i) {
        return ApprovalTask.builder()
                .taskCode(i.getTaskCode() == null ? 0 : i.getTaskCode())
                .entityCode(i.getEntityCode())
                .documentCode(i.getRefCode())
                .documentNumber(i.getSchemeDocNo())
                .levelNo(i.getLevelNo() == null ? 0 : i.getLevelNo())
                .totalLevels(i.getTotalLevels() == null ? 0 : i.getTotalLevels())
                .finalLevel(Boolean.TRUE.equals(i.getIsFinalLevel()))
                .approvedCount(i.getApprovedCount() == null ? 0 : i.getApprovedCount())
                .quorumRequired(i.getQuorumReq() == null ? 0 : i.getQuorumReq())
                .instanceStatus(i.getInstanceStatus())
                .build();
    }
}
