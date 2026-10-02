package com.gsl.reports;

import com.gsl.enums.AppOperation;
import com.gsl.enums.ISubModule;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;

/**
 * Identifies one per-module/sub-module/operation "shard" report — e.g. Finance › APVoucher › ADD —
 * used as the map key for {@code ExtentReport}'s {@code SHARD_EXTENTS} registry and
 * {@link SummaryReportBuilder}'s per-shard pass/fail stats. See {@link ExtentManager#getCurrentShardKey()}
 * for how the active key is tracked per test thread.
 */
public record ShardKey(MainModuleType module, ISubModule subModule, AppOperation operation) {}
