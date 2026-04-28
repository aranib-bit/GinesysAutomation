package com.gsl.model.inventory;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Maps to invgrp — Inventory Group table.
 * Inventory module.
 */
@Data
public class InvGrp {

    public long grpcode;
    public String grpname;
    public String lev1grpname;
    public String lev2grpname;
    public BigDecimal grpcomm;
    public long parcode;
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
