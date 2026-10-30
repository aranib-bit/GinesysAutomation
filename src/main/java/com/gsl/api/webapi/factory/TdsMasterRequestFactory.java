package com.gsl.api.webapi.factory;

import com.gsl.api.webapi.model.request.tds.AccountClassCreateRequest;
import com.gsl.api.webapi.model.request.tds.AgencyUdfValue;
import com.gsl.api.webapi.model.request.tds.TdsAgencyCreateRequest;
import com.gsl.api.webapi.model.request.tds.TdsAgencyLookupQuery;
import com.gsl.api.webapi.model.request.tds.TdsListRequest;
import com.gsl.api.webapi.model.request.tds.TdsRateDetail;
import com.gsl.api.webapi.model.request.tds.TdsSectionCreateRequest;
import com.gsl.api.webapi.model.request.tds.TdsSourceCreateRequest;
import com.gsl.api.webapi.model.request.tds.TdsSourceDetail;
import com.gsl.api.webapi.model.request.tds.UdfConfigQuery;
import com.gsl.api.webapi.model.response.tds.TdsUdfConfig;

import java.math.BigDecimal;
import java.util.List;

import static com.gsl.api.webapi.constants.TdsMasterDefaults.*;

/** Builds the TDS master request bodies and query strings; no HTTP, no shared state. */
public final class TdsMasterRequestFactory {

    private static final String EMPTY = "";

    private TdsMasterRequestFactory() {}

    public static TdsListRequest list(int site, int ou, long user, String workDate, String sort) {
        return TdsListRequest.builder().connectedSite(site).connectedUser(user).connectedSiteOuCode(ou).workDate(workDate)
                .page(1).start(0).limit(LIST_PAGE_SIZE).sort(sort).build();
    }

    public static TdsSourceCreateRequest source(String name, String sourceType, long user) {
        return TdsSourceCreateRequest.builder().sourceCode(0).tdsSource(name).sourceType(sourceType).connectedUser(user).dataVersion(0).build();
    }

    /** An active agency with no address or bank details; {@code udfValues} carries every configured user-defined field. */
    public static TdsAgencyCreateRequest agency(String agencyId, String name, long classCode, long glCode, long user, List<AgencyUdfValue> udfValues) {
        return TdsAgencyCreateRequest.builder()
                .code(0).agencyId(agencyId).name(name).alias(EMPTY).classCode(classCode).classType(EMPTY).glCode(glCode).remarks(EMPTY).address(EMPTY)
                .cityCode(null).city(EMPTY).district(EMPTY).state(EMPTY).std(EMPTY).country(EMPTY).pin(EMPTY).phone1(EMPTY).phone2(EMPTY).phone3(EMPTY)
                .residenceNo(EMPTY).email(EMPTY).email2(EMPTY).fax(EMPTY).mobile(EMPTY).contactName(EMPTY).website(EMPTY).bankName(EMPTY).accountNo(EMPTY)
                .micrCode(EMPTY).ifsCode(EMPTY).rtgsCode(EMPTY).chequeLabel(EMPTY).connectedUser(user).dataVersion(0).extinct(NOT_EXTINCT)
                .udfValues(udfValues)
                .build();
    }

    /** One entry per configured field, valued only when the configuration makes it mandatory and gives a default. */
    public static List<AgencyUdfValue> udfValues(List<TdsUdfConfig> configs) {
        return configs.stream()
                .map(c -> AgencyUdfValue.builder().fieldName(c.getColumnName().toLowerCase())
                        .value(c.isRequired() ? c.getDefaultValue() : null)
                        .datatype(c.getDataType() == null ? null : c.getDataType().toLowerCase()).build())
                .toList();
    }

    /** A TDS nature carrying one rate for one source, valid from {@code dateFrom}. */
    public static TdsSectionCreateRequest section(long user, String tdsName, String section, long agencySlCode, String sectionType, long sourceCode,
                                                  String sourceName, String dateFrom, BigDecimal rate) {
        TdsRateDetail rateDetail = TdsRateDetail.builder().code(0).dateFrom(dateFrom).rate(rate).build();
        TdsSourceDetail detail = TdsSourceDetail.builder().sourceCode(sourceCode).sourceName(sourceName).rateDetails(List.of(rateDetail)).build();
        return TdsSectionCreateRequest.builder().code(0).empCode(user).tdsName(tdsName).section(section).agencySlCode(agencySlCode).sectionType(sectionType)
                .sourceDetails(List.of(detail)).ext(NOT_EXTINCT).build();
    }

    /** An active account class of the TDS Agency type. */
    public static AccountClassCreateRequest agencyClass(String name) {
        return AccountClassCreateRequest.builder().className(name).ext(NOT_EXTINCT).classType(AGENCY_CLASS_TYPE).companyCode(ACCOUNT_CLASS_COMPANY_CODE)
                .code(0).id(ACCOUNT_CLASS_MODEL_ID).build();
    }

    public static UdfConfigQuery udfConfig() {
        return UdfConfigQuery.builder().cacheBuster(System.currentTimeMillis()).module(AGENCY_UDF_MODULE).build();
    }

    /** The agency form's lookup of its class ({@code fields} = class name and type) or ledger, for the connected site. */
    public static TdsAgencyLookupQuery agencyLookup(int site, int ou, long user, String workDate, List<String> queryFields) {
        return TdsAgencyLookupQuery.builder().module(AGENCY_MODULE).connectedSite(site).connectedUserCode(user).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedSiteType(com.gsl.api.webapi.constants.ServiceInvoiceDefaults.SITE_TYPE).workDate(workDate).queryFieldName(queryFields)
                .page(1).start(0).limit(LOOKUP_PAGE_SIZE).build();
    }
}
