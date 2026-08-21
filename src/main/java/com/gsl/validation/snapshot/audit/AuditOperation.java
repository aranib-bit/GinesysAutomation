package com.gsl.validation.snapshot.audit;

/**
 * The business-level operation one {@link AuditChange} represents — distinct from the DB-physical
 * {@code aud2_<table>.action} column, which can legitimately be {@code DELETE} immediately followed
 * by {@code INSERT} for what is, from the business perspective, a single {@link #UPDATE} (see
 * {@link AuditChangeReconstructor}'s reconciliation step, and doc §5/§19 of
 * {@code Software_Data_Audit_Testing_and_Representation.md}).
 */
public enum AuditOperation {
    INSERT,
    UPDATE,
    DELETE
}
