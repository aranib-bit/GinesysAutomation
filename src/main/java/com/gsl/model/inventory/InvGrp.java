package com.gsl.model.inventory;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Maps to invgrp — the Inventory Group master table, which classifies inventory items into a
 * multi-level hierarchy ({@link #lev}/{@link #parcode}). Fetched via {@link
 * com.gsl.database.dao.inventory.InvGrpDao} for Inventory-module test data setup/verification
 * (e.g. Product Definition group-creation tests look a group up by name and compare row counts
 * before/after a create action).
 *
 * <p>This class supersedes the identically-named, now-{@code @Deprecated} stub at
 * {@link com.gsl.model.InvGrp} in the root {@code com.gsl.model} package.
 *
 * <p>Most fields below mirror {@code invgrp} columns directly and are only exercised through this
 * DAO today (row-count/lookup checks), so their individual business semantics beyond the
 * hierarchy fields are not yet documented here — see {@code invgrp}'s own schema/data dictionary
 * if a test needs to assert on one of them.
 */
@Data
public class InvGrp {

    public long grpcode;
    public String grpname;
    /** Display name of the level-1 (top-level) ancestor group above this one in the hierarchy. */
    public String lev1grpname;
    /** Display name of the level-2 ancestor group above this one in the hierarchy. */
    public String lev2grpname;
    public BigDecimal grpcomm;
    /** {@code invgrp.code} of this group's immediate parent group in the hierarchy (see {@link #lev}). */
    public long parcode;
    /** Hierarchy depth of this group — {@code 1} is top-level; a query with {@code lev = 1} (see
     *  {@code InvGrpDao#getGroupByName}) fetches only top-level groups. */
    public long lev;
    public long ind;
    public String rem;
    public String cat1prompt;
    public String cat2prompt;
    public String cat3prompt;
    public String cat4prompt;
    public String cat5prompt;
    public String cat6prompt;
    public long seq;
    public long taxcode;
    public String attr1prompt;
    public String attr2prompt;
    public String attr3prompt;
    public String attr4prompt;
    public String attr5prompt;
    public String attr6prompt;
    public long routecode;
    public long excisemainCode;
    public String imgConvention;
    public BigDecimal grpBarunit;
    public String grpUnitname;
    public BigDecimal grpPriceChangeLimit;
    public long invhsnsacmainCode;
    public long doccode;
}
