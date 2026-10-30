package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.factory.TdsMasterRequestFactory;
import com.gsl.api.webapi.model.request.tds.AgencyUdfValue;
import com.gsl.api.webapi.model.request.tds.TdsSectionCreateRequest;
import com.gsl.api.webapi.model.response.tds.TdsAgencyItem;
import com.gsl.api.webapi.model.response.tds.TdsAgencyListResponse;
import com.gsl.api.webapi.model.response.tds.TdsSectionItem;
import com.gsl.api.webapi.model.response.tds.TdsSectionListResponse;
import com.gsl.api.webapi.model.response.tds.TdsSourceItem;
import com.gsl.api.webapi.model.response.tds.TdsSourceListResponse;
import com.gsl.api.webapi.model.response.tds.TdsUdfConfig;
import io.restassured.specification.RequestSpecification;

import java.util.List;

import static com.gsl.api.webapi.constants.TdsMasterDefaults.*;

/**
 * Finance API client of the TDS masters: source, agency and section (nature) lists and creates, plus the agency form's class, ledger and
 * user-defined-field lookups. HTTP only; the find-or-create chain is {@link com.gsl.service.TdsMasterService}. Creates answer 201 with the
 * created name and no code, so a caller finds the new record by listing.
 */
public class TdsMasterApiClient extends ApiClient {

    /** The site/OU/user the screens are opened for. */
    public record Context(int site, int ou, long empCode, String workDate) {}

    public TdsMasterApiClient(RequestSpecification spec) {
        super(spec);
    }

    public List<TdsSourceItem> listSources(Context c) {
        return bodyAs(postRaw(WebApiEndpoints.Tds.SOURCE_LIST, TdsMasterRequestFactory.list(c.site(), c.ou(), c.empCode(), c.workDate(), SOURCE_SORT)),
                TdsSourceListResponse.class).items();
    }

    /** {@code POST tdssource}: a corporate source. */
    public void createSource(Context c, String name) {
        postRaw(WebApiEndpoints.Tds.SOURCE_CREATE, TdsMasterRequestFactory.source(name, SOURCE_TYPE_CORPORATE, c.empCode()));
    }

    public List<TdsAgencyItem> listAgencies(Context c) {
        return bodyAs(postRaw(WebApiEndpoints.Tds.AGENCY_LIST, TdsMasterRequestFactory.list(c.site(), c.ou(), c.empCode(), c.workDate(), AGENCY_SORT)),
                TdsAgencyListResponse.class).items();
    }

    /** {@code POST tdsagency}. */
    public void createAgency(Context c, String agencyId, String name, long classCode, long glCode, List<AgencyUdfValue> udfValues) {
        postRaw(WebApiEndpoints.Tds.AGENCY_CREATE, TdsMasterRequestFactory.agency(agencyId, name, classCode, glCode, c.empCode(), udfValues));
    }

    public List<TdsSectionItem> listSections(Context c) {
        return bodyAs(postRaw(WebApiEndpoints.Tds.SECTION_LIST, TdsMasterRequestFactory.list(c.site(), c.ou(), c.empCode(), c.workDate(), SECTION_SORT)),
                TdsSectionListResponse.class).items();
    }

    /** {@code POST tdssection/tdssection}. */
    public void createSection(TdsSectionCreateRequest request) {
        postRaw(WebApiEndpoints.Tds.SECTION_CREATE, request);
    }

    /** {@code POST AccountClass/Create}: a new TDS Agency class; the response carries no code, so callers find it through {@link #fetchAgencyClass}. */
    public void createAgencyClass(String name) {
        postRaw(WebApiEndpoints.Tds.ACCOUNT_CLASS_CREATE, TdsMasterRequestFactory.agencyClass(name));
    }

    /** The class the TDS Agency screen offers ({@code SubLedgers?module=tdsagency}, class type D), or null when it offers none. */
    public SubLedgerLookupResult fetchAgencyClass(Context c) {
        List<SubLedgerLookupResult> classes = getRawQuery(WebApiEndpoints.Voucher.SUB_LEDGERS,
                TdsMasterRequestFactory.agencyLookup(c.site(), c.ou(), c.empCode(), c.workDate(), AGENCY_CLASS_QUERY_FIELDS))
                .jsonPath().getList("result.items", SubLedgerLookupResult.class);
        return classes.stream().filter(x -> AGENCY_CLASS_TYPE.equalsIgnoreCase(x.getClassType())).findFirst().orElse(null);
    }

    /** The ledgers the TDS Agency screen offers ({@code Ledgers?module=tdsagency}). */
    public List<GlLedgerLookupResult> fetchAgencyLedgers(Context c) {
        return getRawQuery(WebApiEndpoints.Voucher.LEDGERS,
                TdsMasterRequestFactory.agencyLookup(c.site(), c.ou(), c.empCode(), c.workDate(), AGENCY_LEDGER_QUERY_FIELDS))
                .jsonPath().getList("result.items", GlLedgerLookupResult.class);
    }

    /** The agency's configured user-defined fields ({@code udfconfigurations?module=financetdsagency}). */
    public List<TdsUdfConfig> fetchAgencyUdfConfigs() {
        return getRawQuery(WebApiEndpoints.Tds.UDF_CONFIG, TdsMasterRequestFactory.udfConfig()).jsonPath().getList("result", TdsUdfConfig.class);
    }
}
