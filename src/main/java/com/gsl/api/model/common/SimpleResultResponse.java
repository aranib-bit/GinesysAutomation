package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The trivial {@code {result, success, error}} envelope shared verbatim by both
 * {@code POST /Ledger/Create} and {@code POST /EmployeeMaster/CreateFinanceSubLedgerOther} —
 * confirmed via HAR ({@code CreateExpenseSiteApplicable...har}, {@code CreateSubLedgerOthers...har}):
 * {@code {"result":true,"success":true,"error":null}}. Unlike {@code ArVoucherCreateResponse}'s
 * {@code result}, this endpoint pair's {@code result} is a plain boolean, not a nested object —
 * neither response carries back a generated code, so callers must read the created row back from
 * the DB by the unique name they sent (see {@code LedgerMasterBackfillStrategy}/
 * {@code SubLedgerOthersBackfillStrategy}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleResultResponse {
    private boolean result;
    private boolean success;
    private String error;
}
