package com.gsl.service.party;

/** Where a party is looked up or created: the connected site, its organisational unit and the work date ({@code yyyy-MM-ddT00:00:00}). */
public record PartyContext(int site, int ou, String workDate) {}
