package com.gsl.api.webapi.factory;

import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.api.webapi.constants.PartyDefaults;
import com.gsl.api.webapi.model.request.party.CustomerCreateRequest;
import com.gsl.api.webapi.model.request.party.TdsException;
import com.gsl.api.webapi.model.request.party.VendorMasterCreateRequest;
import com.gsl.utils.Faker.party.PartyProfile;

import java.util.ArrayList;
import java.util.List;

/** Builds party create requests from the values a caller must supply; everything else comes from the POJO defaults (the captured HAR values). Stateless. */
public final class PartyRequestFactory {

    private PartyRequestFactory() {}

    /** Who the party is. {@code classCode} decides the kind (Supplier, Jobber, ...). */
    public record Identity(String name, String slId, String pan, String contactPerson, String mobile, long classCode, long glCode) {
        /** Identity of a generated party: this name and id, the profile's PAN, contact person and mobile. */
        public static Identity fromProfile(PartyProfile profile, String name, String slId, long classCode, long glCode) {
            return new Identity(name, slId, profile.pan(), profile.contactPerson(), profile.mobile(), classCode, glCode);
        }

        /** Identity with the placeholder contact data. */
        public static Identity withPlaceholderContact(String name, String slId, String pan, long classCode, long glCode) {
            return new Identity(name, slId, pan, PartyDefaults.PLACEHOLDER_CONTACT_PERSON, PartyDefaults.PLACEHOLDER_MOBILE, classCode, glCode);
        }
    }

    /** A {@code VendorMaster/Create} body for any Vendor Master class type; {@code natures} is only used when {@code tdsSourceCode} is set; {@code companyCode} is mandatory on the server. */
    public static VendorMasterCreateRequest vendor(Identity id, long tradeGroup, long purchaseTerm, long taxRegion, long gstCategory,
                                                   Long tdsSourceCode, List<TdsException> natures, List<UdfValueEntry> udf, Long companyCode) {
        boolean tds = tdsSourceCode != null;
        return VendorMasterCreateRequest.builder()
                .slname(id.name()).slid(id.slId()).pan(id.pan()).bcp(id.contactPerson()).bfX2(id.mobile())
                .clscode(id.classCode()).glcode(id.glCode())
                .fintradegrpcode(tradeGroup).purtermmaincode(purchaseTerm).admcmptaxcode(taxRegion).cpcategorycode(gstCategory)
                .cmpcode(companyCode)
                .tdsapp(tds ? "Y" : "N").tdssrccode(tds ? tdsSourceCode : null)
                .finTDSExceptions(tds && natures != null ? new ArrayList<>(natures) : new ArrayList<>())
                .udfDetails(udf == null ? new ArrayList<>() : new ArrayList<>(udf))
                .build();
    }

    /** A {@code Customer/Create} body. {@code priceList} may be null when the environment has no sale price list. */
    public static CustomerCreateRequest customer(Identity id, long tradeGroup, long salesTerm, long purchaseTerm, Long priceList, String priceType,
                                                 long taxRegion, long gstCategory, List<UdfValueEntry> udf) {
        return CustomerCreateRequest.builder()
                .slname(id.name()).slid(id.slId()).pan(id.pan()).bcp(id.contactPerson()).bfX2(id.mobile())
                .clscode(id.classCode()).glcode(id.glCode())
                .fintradegrpcode(tradeGroup).saltradegrpcode(tradeGroup).saltermcode(salesTerm).purtermmaincode(purchaseTerm)
                .pricelistcode(priceList).pricetype(priceType == null ? PartyDefaults.DEFAULT_PRICE_TYPE : priceType)
                .admcmptaxcode(taxRegion).cpcategorycode(gstCategory)
                .saletype(PartyDefaults.CUSTOMER_SALE_TYPE).salepostmethod(null)
                .udfDetails(udf == null ? new ArrayList<>() : new ArrayList<>(udf))
                .build();
    }
}
