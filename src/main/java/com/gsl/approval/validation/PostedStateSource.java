package com.gsl.approval.validation;

import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.PostedState;

/** Reads whether an approved document has posted. */
public interface PostedStateSource {

    PostedState read(ApprovalRequest request);
}
