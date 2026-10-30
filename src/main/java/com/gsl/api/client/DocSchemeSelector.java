package com.gsl.api.client;

import com.gsl.api.core.ApiException;
import com.gsl.api.model.common.DocNumberSchemeEntry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Picks the {@link DocNumberSchemeEntry} an API voucher create should use, shared by
 * {@code ArVoucherApiClient} and {@code ApVoucherApiClient} (they carried identical copies).
 *
 * <p>The API's {@code docnumberschemes} list carries no capacity information, so the caller supplies
 * the scheme NAME it already decided on (from {@code DocSchemeResolver}, which reads the counters
 * from the DB). When no name is supplied the legacy behaviour is kept for DB-free callers: prefer
 * {@code legacyPreferredName}, otherwise the first entry.
 */
public final class DocSchemeSelector {

    private DocSchemeSelector() {}

    /**
     * @param schemes             the API's list for the voucher menu
     * @param requestedName       scheme name chosen by the resolver, or {@code null} for the legacy pick
     * @param legacyPreferredName name preferred when {@code requestedName} is null
     * @param voucherLabel        "AR Voucher"/"AP Voucher" — for the error message only
     * @return the matching entry, or {@code null} only in legacy mode when the list is empty
     * @throws ApiException when {@code requestedName} is set but the API does not offer it
     */
    public static DocNumberSchemeEntry select(List<DocNumberSchemeEntry> schemes, String requestedName,
                                       String legacyPreferredName, String voucherLabel) {
        if (requestedName != null && !requestedName.isBlank()) {
            return schemes.stream()
                    .filter(s -> requestedName.equalsIgnoreCase(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(
                            "Doc scheme '" + requestedName + "' chosen for " + voucherLabel
                                    + " is not offered by GET docnumberschemes. Offered: "
                                    + schemes.stream().map(DocNumberSchemeEntry::getName)
                                            .collect(Collectors.joining(", ", "[", "]"))
                                    + ". Check admou_docscheme tagging for the owner site.", 0));
        }
        return schemes.stream()
                .filter(s -> legacyPreferredName.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
    }
}
