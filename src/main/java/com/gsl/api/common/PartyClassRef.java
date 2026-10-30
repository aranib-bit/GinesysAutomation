package com.gsl.api.common;

/** One sub-ledger class from {@code Shared/GetSubledgerClassLookup}: its code, name and class type letter ({@code S, J, A, T, E, H, C, ...}). */
public record PartyClassRef(long code, String name, char type) {

    /** The party kind family a class of this type is created through (Vendor Master for S/J/A/T/E/H, Customer for C), or null when unsupported. */
    public PartyKind.Family family() {
        for (PartyKind kind : PartyKind.values()) {
            if (kind.classType() == type) return kind.family();
        }
        return null;
    }
}
