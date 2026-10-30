package com.gsl.api.webapi.client;

import com.gsl.api.client.MasterDataSupport;
import com.gsl.api.common.CityRef;
import com.gsl.api.common.PartyKind;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.Endpoint;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.party.PartyListRequest;
import com.gsl.api.webapi.model.response.party.PartyCityResponse;
import com.gsl.api.webapi.model.response.party.PartyListItem;
import com.gsl.api.webapi.model.response.party.PartyListResponse;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.List;

/** The party list calls of the Vendor Master family and Customer, typed. Creation stays in the vendor/customer clients (their environment-code lookups). */
public class PartyMasterWebApiClient extends ApiClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;
    private static final int CITY_PAGE_SIZE = 1000;
    /** The city master rarely changes: one read per JVM and site. */
    private static final java.util.Map<Integer, List<CityRef>> CITY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public PartyMasterWebApiClient(RequestSpecification spec) {
        super(spec);
    }

    private static Endpoint listEndpoint(PartyKind.Family family) {
        return switch (family) {
            case VENDOR_MASTER -> WebApiEndpoints.Party.VENDOR_LIST;
            case CUSTOMER -> WebApiEndpoints.Party.CUSTOMER_LIST;
            default -> throw new UnsupportedOperationException("No party list endpoint for " + family);
        };
    }

    /** Every party of the family, paged until the reported total is reached. */
    public List<PartyListItem> list(PartyKind.Family family, int connectedSite, int connectedOuCode, String workDate) {
        List<PartyListItem> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            PartyListRequest request = PartyListRequest.builder().connectedSite(String.valueOf(connectedSite))
                    .connectedSiteOuCode(connectedOuCode).workDate(workDate).page(page).start((page - 1) * PAGE_SIZE).limit(PAGE_SIZE).build();
            PartyListResponse response = post(listEndpoint(family), request, PartyListResponse.class);
            if (response.items().isEmpty()) break;
            all.addAll(response.items());
            if (all.size() >= response.totalCount()) break;
        }
        return all;
    }

    /** The active cities of the city master ({@code Shared/GetCityLookup}), read once per JVM and site. */
    public List<CityRef> cities(int connectedSite, int connectedOuCode, String workDate) {
        return CITY_CACHE.computeIfAbsent(connectedSite, site -> {
            List<CityRef> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                java.util.Map<String, Object> body = MasterDataSupport.lookupBody(site, connectedOuCode, workDate,
                        List.of("code", "district", "stateName", "country", "zone"), true);
                body.put("page", page);
                body.put("start", (page - 1) * CITY_PAGE_SIZE);
                body.put("limit", CITY_PAGE_SIZE);
                PartyCityResponse response = post(WebApiEndpoints.Party.CITY_LOOKUP, body, PartyCityResponse.class);
                if (response.items().isEmpty()) break;
                for (PartyCityResponse.Item it : response.items()) {
                    if (!"Y".equalsIgnoreCase(it.getExtinct())) all.add(new CityRef(it.getName(), it.getState(), it.getCountry()));
                }
                if (page * CITY_PAGE_SIZE >= response.totalCount()) break;
            }
            return all;
        });
    }
}
