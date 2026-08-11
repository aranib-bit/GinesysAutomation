package com.gsl.api.model.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code LedgerCreateRequest.grpcode} — a single-field object reference, not a bare number.
 * Shape confirmed via HAR: {@code "grpcode":{"code":"44"}}. {@code code} must be a leaf
 * {@code fingrp} node for the ledger's {@code type} (see {@code LedgerApiClient}'s leaf-group
 * filter, driven by {@code FinAccGrpDao.countChildren}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerGroupRef {
    private String code;
}
