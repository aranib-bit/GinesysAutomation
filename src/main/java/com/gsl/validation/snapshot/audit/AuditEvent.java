package com.gsl.validation.snapshot.audit;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One whole business transaction's audit footprint — doc §12/§21's "AuditEvent" concept: the
 * {@code aud2_transaction} row (who/when/module/program) plus every {@link AuditChange} that
 * transaction produced across however many {@code aud2_<table>}s it touched (the
 * {@code aud2_transaction_table} fan-out).
 *
 * @param transactionId   {@code aud2_transaction.transaction_id}
 * @param user            {@code aud2_transaction.login_user}
 * @param module          {@code aud2_transaction.module_shortcode}
 * @param documentType    human-readable document type, e.g. {@code "General Voucher"}
 * @param documentNumber  the document's own number/code, e.g. {@code scheme_docno} or {@code vchcode}
 * @param timestamp       {@code aud2_transaction.transaction_date}
 * @param changes         every {@link AuditChange} produced by this one transaction, across all tables
 */
public record AuditEvent(
        String transactionId,
        String user,
        String module,
        String documentType,
        String documentNumber,
        LocalDateTime timestamp,
        List<AuditChange> changes
) {
}
