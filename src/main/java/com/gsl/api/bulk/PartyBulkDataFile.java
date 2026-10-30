package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.CustomerApiClient;
import com.gsl.api.client.SubLedgerOthersApiClient;
import com.gsl.api.client.VendorApiClient;
import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.common.CityRef;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.common.PartyKind;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.model.subledgerothers.FinSlGlEntry;
import com.gsl.api.model.subledgerothers.FinSlGlSiteInfo;
import com.gsl.api.model.subledgerothers.SubLedgerOthersCreateRequest;
import com.gsl.api.model.subledgerothers.SubledgerClassLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.api.webapi.client.PartyMasterWebApiClient;
import com.gsl.api.webapi.model.response.party.PartyListItem;
import com.gsl.config.ConfigFactory;
import com.gsl.service.party.PartyContext;
import com.gsl.utils.Faker.party.PartyDataFactory;
import com.gsl.utils.Faker.party.PartyProfile;
import org.testng.SkipException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands bulk party work to k6: Java builds N complete request bodies (the same request POJOs, environment codes and generated party data
 * the WebAPI path uses, so k6 can never drift from the contract) and writes them to {@code <k6.home>/data/generated/parties-<runId>.json};
 * the k6 scenario only POSTs them. One file shape serves every operation: create (Vendor Master family, Customer, Others) and set-extinct
 * ({@code VendorMaster/Update}). The file is a run artifact (git-ignored) and lives inside the project.
 */
public final class PartyBulkDataFile {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OTHERS_CLASS_TYPE = "O";

    private PartyBulkDataFile() {}

    /** What every sub-ledger id of a run starts with; lets the run be found again in the party list. */
    public static String slIdPrefix(String runId) {
        return "ZB" + runId;
    }

    private static String slId(String runId, int i) {
        String slId = slIdPrefix(runId) + i;
        return slId.substring(0, Math.min(slId.length(), 25));
    }

    /**
     * Create bodies for a Vendor Master family class or Customer.
     *
     * @param runId unique per run; becomes part of every name and sl id and of the file name
     * @return the written file
     * @throws SkipException when the environment lacks what a party of this class needs (same reasons as the single-party path)
     */
    public static Path write(PartyClassRef cls, int count, PartyContext c, String runId, VendorApiClient vendors, CustomerApiClient customers,
                             PartyMasterWebApiClient lists) {
        PartyKind.Family family = cls.family();
        if (family == null) throw new UnsupportedOperationException("No bulk party creation for class type " + cls.type());
        List<UdfValueEntry> udf = UdfValueBuilder.fetchAndBuild(vendors, "FINSL");
        List<CityRef> cities = lists.cities(c.site(), c.ou(), c.workDate());

        Object env = family == PartyKind.Family.CUSTOMER ? customerEnv(customers, c) : vendorEnv(vendors, cls, c);
        List<Object> bodies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = "ZZ API " + cls.name().toUpperCase() + " " + runId + " " + i;
            PartyProfile profile = PartyDataFactory.generator().next(ConfigFactory.getPartyDataConfig().completeness());
            bodies.add(family == PartyKind.Family.CUSTOMER
                    ? CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec(name, slId(runId, i), cls.code()), (CustomerApiClient.EnvCodes) env, udf, profile, cities)
                    : VendorApiClient.buildCreateRequest(new VendorApiClient.VendorSpec(name, slId(runId, i), false, List.of()), (VendorApiClient.EnvCodes) env, 0, udf, profile, cities));
        }
        return writeFile("CreateParty", "CREATE", family == PartyKind.Family.CUSTOMER ? "CUSTOMER_CREATE" : "VENDOR_CREATE", cls.code(), runId, bodies);
    }

    /**
     * Create bodies for Sub Ledger "Others" ({@code EmployeeMaster/CreateFinanceSubLedgerOther}); each one links the sub-ledger to
     * {@code glcode} (the General Voucher Type3 case), narrowed to {@code siteCode} when the GL is site-applicable.
     *
     * @throws SkipException when the environment has no Others class
     */
    public static Path writeOthers(int count, long glcode, String glname, Long siteCode, String runId, SubLedgerOthersApiClient others) {
        List<SubledgerClassLookupResult> classes = others.fetchSubledgerClassLookup(OTHERS_CLASS_TYPE);
        if (classes.isEmpty()) {
            throw new SkipException("Skipped due to lack of data: GetSubledgerClassLookup?classType=O returned no class - Others sub-ledgers cannot be created.");
        }
        long clscode = classes.get(0).getCode();
        List<UdfValueEntry> udf = UdfValueBuilder.fetchAndBuild(others, "FINSL");
        List<FinSlGlSiteInfo> sites = siteCode != null ? List.of(FinSlGlSiteInfo.builder().siteCode(siteCode).build()) : List.of();
        List<Object> bodies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bodies.add(MAPPER.convertValue(SubLedgerOthersCreateRequest.builder()
                    .slname("ZZ API OTHERS " + runId + " " + i)
                    .slid(slId(runId, i))
                    .clscode(clscode)
                    .glcode(glcode)
                    .finSLGLs(List.of(FinSlGlEntry.builder().glcode(glcode).glname(glname).finSLGLSites(sites).build()))
                    .udfDetails(udf)
                    .build(), Map.class));
        }
        return writeFile("CreateParty", "CREATE", "OTHERS_CREATE", clscode, runId, bodies);
    }

    /**
     * {@code VendorMaster/Update} bodies that set the extinct flag of this run's own throw-away vendors (never of any other party).
     * Only parties whose sub-ledger id carries the run's prefix are touched, and only those not already in the requested state.
     *
     * @throws DataSetupException when the run has no vendor-family party to change
     */
    public static Path writeExtinct(boolean extinct, PartyContext c, String runId, VendorApiClient vendors, PartyMasterWebApiClient lists) {
        String prefix = slIdPrefix(runId);
        List<Object> bodies = new ArrayList<>();
        for (PartyListItem p : lists.list(PartyKind.Family.VENDOR_MASTER, c.site(), c.ou(), c.workDate())) {
            if (p.getSlId() == null || !p.getSlId().startsWith(prefix) || p.isExtinct() == extinct) continue;
            bodies.add(VendorApiClient.buildUpdateBody(vendors.fetchVendorRecord(p.getCode()), extinct));
        }
        if (bodies.isEmpty()) {
            throw new DataSetupException("[PartyBulkDataFile] run '" + runId + "' has no vendor-family party to mark " + (extinct ? "extinct" : "active"));
        }
        return writeFile("SetExtinct", "UPDATE", "VENDOR_UPDATE", 0, runId + "-" + (extinct ? "extinct" : "active"), bodies);
    }

    private static Path writeFile(String logTag, String operation, String endpoint, long classCode, String runId, List<Object> bodies) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("operation", operation);
        file.put("logTag", logTag);
        file.put("endpointGroup", "Party");
        file.put("endpoint", endpoint);
        file.put("classCode", classCode);
        file.put("runId", runId);
        file.put("bodies", bodies);
        return BulkDataFiles.write("parties", runId, file);
    }

    private static CustomerApiClient.EnvCodes customerEnv(CustomerApiClient customers, PartyContext c) {
        return customers.fetchEnvCodes(c.site(), c.ou(), c.workDate()).orElseThrow(() -> new SkipException(
                "Skipped due to lack of data: the environment lacks a receivable ledger, a trade group with sales and purchase terms, a tax region or an 'Unregistered' GST category - customers cannot be created."));
    }

    private static VendorApiClient.EnvCodes vendorEnv(VendorApiClient vendors, PartyClassRef cls, PartyContext c) {
        VendorApiClient.EnvCodes existing = vendors.fetchEnvCodesFromExistingVendor(c.site(), c.ou(), c.workDate());
        if (existing == null) {
            throw new SkipException("Skipped due to lack of data: the Vendor Master has no party to copy the trade group, purchase term, tax region and ledger from.");
        }
        return new VendorApiClient.EnvCodes(cls.code(), existing.glcode(), existing.fintradegrpcode(), existing.purtermmaincode(),
                existing.admcmptaxcode(), existing.cpcategorycode(), existing.cmpcode());
    }
}
