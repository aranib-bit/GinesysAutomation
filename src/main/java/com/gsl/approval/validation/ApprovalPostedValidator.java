package com.gsl.approval.validation;

import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.PostedState;
import com.gsl.approval.service.ApprovalPoller;

import java.util.Optional;

/** After the final approval: waits (bounded, see {@link ApprovalPoller}) for the document to post and reports POSTED, UNPOSTED or UNKNOWN. */
public class ApprovalPostedValidator {

    private final PostedStateSource source;
    private final ApprovalPoller poller;

    public ApprovalPostedValidator(PostedStateSource source, ApprovalPoller poller) {
        this.source = source;
        this.poller = poller;
    }

    public PostedState awaitPosted(ApprovalRequest request) {
        Optional<PostedState> settled = poller.until(() -> {
            PostedState state = source.read(request);
            return state == PostedState.UNPOSTED ? Optional.empty() : Optional.of(state);
        });
        return settled.orElse(PostedState.UNPOSTED);
    }
}
