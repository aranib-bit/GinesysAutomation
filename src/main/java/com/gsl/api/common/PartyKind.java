package com.gsl.api.common;

/**
 * The kinds of party master the framework creates. The class type letter is the one {@code admcls.clstype} / {@code Shared/GetSubledgerClassLookup}
 * uses; the {@link Family} decides which endpoint family and which request POJO serve it. Adding a kind is one row here - Jobber, Agent,
 * Transporter, Channel and Staff are created through the same {@code VendorMaster/Create} body as a Supplier (Jobber HAR: identical keys, only
 * {@code clscode} differs).
 */
public enum PartyKind {
    SUPPLIER('S', Family.VENDOR_MASTER),
    JOBBER('J', Family.VENDOR_MASTER),
    AGENT('A', Family.VENDOR_MASTER),
    TRANSPORTER('T', Family.VENDOR_MASTER),
    CHANNEL('H', Family.VENDOR_MASTER),
    STAFF('E', Family.VENDOR_MASTER),
    CUSTOMER('C', Family.CUSTOMER),
    OTHERS('O', Family.OTHERS),
    TDS_AGENCY('D', Family.TDS_AGENCY);

    /** Which endpoint family / request POJO a kind belongs to. */
    public enum Family { VENDOR_MASTER, CUSTOMER, OTHERS, TDS_AGENCY }

    private final char classType;
    private final Family family;

    PartyKind(char classType, Family family) {
        this.classType = classType;
        this.family = family;
    }

    public char classType() {
        return classType;
    }

    public Family family() {
        return family;
    }
}
