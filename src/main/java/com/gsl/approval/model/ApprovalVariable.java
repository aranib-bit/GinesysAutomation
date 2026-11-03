package com.gsl.approval.model;

/**
 * One rule variable an entity exposes ({@code ApprovalRule/GetVariables}). Implemented by a per-entity enum (for AP: {@code ApApprovalVariable});
 * the live {@code GetVariables} answer stays the source of truth, this only says how the framework treats each code.
 */
public interface ApprovalVariable {

    /** The wire code, e.g. {@code TOTAL_AMOUNT}. */
    String varCode();

    /** {@code H} header or {@code L} line. */
    String basis();

    ApprovalDataType dataType();
}
