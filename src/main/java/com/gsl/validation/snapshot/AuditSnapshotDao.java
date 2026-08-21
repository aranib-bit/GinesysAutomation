package com.gsl.validation.snapshot;

import java.util.List;
import java.util.Map;

/**
 * Fetches one {@code aud2_<table>} audit-shadow table's rows for a document — implemented per
 * voucher type (e.g. {@code Aud2SnapshotDao} for General Voucher) so the generic
 * {@link SnapshotService} never depends on any voucher-type-specific audit wiring directly.
 */
public interface AuditSnapshotDao {

    /**
     * @param auditTableName the {@code aud2_<table>} table name (already validated by the
     *                       caller's resolver against a known audit-active registry — never
     *                       raw external input, see {@code Aud2SnapshotDao}'s Javadoc)
     */
    List<Map<String, Object>> fetch(String auditTableName, DocumentContext context);
}
