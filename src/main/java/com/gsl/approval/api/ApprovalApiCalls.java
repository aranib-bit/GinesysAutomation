package com.gsl.approval.api;

import com.gsl.api.core.ApiException;
import com.gsl.approval.service.ApprovalConfigurationException;

import java.util.function.Supplier;

/** Runs one approval API call and turns every failure into an {@link ApprovalConfigurationException} of the right kind. */
final class ApprovalApiCalls {

    private ApprovalApiCalls() {}

    static <T> T read(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            throw new ApprovalConfigurationException(ApprovalConfigurationException.kindForStatus(e.getStatusCode()),
                    operation + " failed (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (ApprovalConfigurationException e) {
            throw e;
        } catch (RuntimeException e) {
            // Transport failures (timeout, connection reset) and unreadable bodies surface as plain runtime exceptions from the HTTP layer.
            throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.COMMUNICATION,
                    operation + " failed: " + e.getMessage(), e);
        }
    }
}
