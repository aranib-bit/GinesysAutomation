package com.gsl.service.party;

import com.gsl.api.client.MasterDataSupport;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.core.ApiException;
import com.gsl.reports.ReportLogger;
import org.testng.SkipException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Ensures a party of a required class exists: if the caller's {@code offered} check already passes nothing happens; otherwise a throw-away
 * party is created through the first platform that can (in the configured order) and the service waits until the master and the caller's check
 * see it. Falls through to the next platform on a transport failure or an unsupported operation; a business error (duplicate id, mandatory
 * field) is never retried on another platform.
 */
public final class PartyMasterService {

    private static final long VISIBLE_TIMEOUT_MS = 30_000L;
    private static final long POLL_MS = 1_500L;

    private final List<PartyMasterGateway> gateways;
    private final long timeoutMs;

    public PartyMasterService(List<PartyMasterGateway> gateways) {
        this(gateways, VISIBLE_TIMEOUT_MS);
    }

    public PartyMasterService(List<PartyMasterGateway> gateways, long timeoutMs) {
        this.gateways = gateways;
        this.timeoutMs = timeoutMs;
    }

    /**
     * @param classCode the class a party is needed in
     * @param offered   true when the consumer (e.g. the Finance sub-ledger lookup) already offers a party of the class
     * @throws SkipException when no platform can create a party of the class
     * @throws ApiException  on a business error, or when the created party never becomes visible
     */
    public void ensurePartyOfClass(long classCode, PartyContext context, BooleanSupplier offered) {
        if (offered.getAsBoolean()) return;
        createParty(classCode, context, created -> offered);
    }

    /** The party {@link #createParty} made: its name and unique id, which is how a consumer finds it again. */
    public record CreatedParty(String name, String slId) {}

    /**
     * Always creates a NEW throw-away party of the class (never reuses one), then waits until it is in its master and {@code offeredWhen} reports it
     * visible to the consumer. Used when a scenario needs a party of its own, e.g. a FIFO set-off that claims every pending document of the party.
     *
     * @throws SkipException when no platform can create a party of the class
     * @throws ApiException  on a business error, or when the created party never becomes visible
     */
    public CreatedParty createParty(long classCode, PartyContext context, java.util.function.Function<CreatedParty, BooleanSupplier> offeredWhen) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String slId = "ZP" + (System.currentTimeMillis() % 1_000_000L);
        String lastReason = "no platform configured";

        for (PartyMasterGateway gateway : gateways) {
            PartyClassRef cls;
            String name;
            try {
                cls = gateway.classes(context).stream().filter(c -> c.code() == classCode).findFirst().orElse(null);
                if (cls == null) {
                    lastReason = "class " + classCode + " is not an active class of a creatable party on " + gateway.platform();
                    continue;
                }
                name = "ZZ API " + cls.name().toUpperCase() + " " + stamp;
                ReportLogger.dataBackfill("[PartyMasterService] No party of class '{}' ({}, type {}) is offered - creating '{}' (slid={}) via {}.",
                        cls.name(), classCode, cls.type(), name, slId, gateway.platform());
                gateway.create(cls, name, slId, context);
            } catch (UnsupportedOperationException e) {
                lastReason = gateway.platform() + ": " + e.getMessage();
                continue;
            } catch (ApiException e) {
                if (!isTransport(e)) throw e;
                lastReason = gateway.platform() + ": " + e.getMessage();
                ReportLogger.dataBackfillWarn("[PartyMasterService] {} failed ({}) - trying the next platform.", gateway.platform(), e.getMessage());
                continue;
            }
            // Created: from here on a failure is final (creating the same party on another platform would duplicate it).
            awaitVisible(gateway, cls, name, slId, context, offeredWhen.apply(new CreatedParty(name, slId)));
            return new CreatedParty(name, slId);
        }
        throw new SkipException("Skipped due to lack of data: no platform could create a party of class " + classCode + " (" + lastReason + ").");
    }

    private void awaitVisible(PartyMasterGateway gateway, PartyClassRef cls, String name, String slId, PartyContext context, BooleanSupplier offered) {
        if (MasterDataSupport.pollFor(timeoutMs, POLL_MS, () -> gateway.exists(cls.family(), slId, context) ? Boolean.TRUE : null) == null) {
            throw new ApiException("Party '" + name + "' (slid=" + slId + ") was created but did not appear in its master within " + timeoutMs / 1000 + "s.", 0);
        }
        if (MasterDataSupport.pollFor(timeoutMs, POLL_MS, () -> offered.getAsBoolean() ? Boolean.TRUE : null) == null) {
            throw new ApiException("Party '" + name + "' exists in its master but is not offered for class " + cls.code() + " within " + timeoutMs / 1000
                    + "s - the lookup may be scoped by site/OU.", 0);
        }
        ReportLogger.dataBackfill("[PartyMasterService] '{}' is now offered.", name);
    }

    /** A failure of the platform itself (no HTTP status, or a server error) - as opposed to a business refusal (4xx). */
    private static boolean isTransport(ApiException e) {
        return e.getStatusCode() == 0 || e.getStatusCode() >= 500;
    }
}
