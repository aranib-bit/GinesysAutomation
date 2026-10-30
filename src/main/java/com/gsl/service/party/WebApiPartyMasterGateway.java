package com.gsl.service.party;

import com.gsl.api.client.CustomerApiClient;
import com.gsl.api.client.VendorApiClient;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.common.PartyKind;
import com.gsl.api.webapi.client.PartyMasterWebApiClient;
import com.gsl.enums.ApiPlatform;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** WebAPI implementation: classes and environment codes from the {@code Shared/*} lookups, creation through {@code VendorMaster/Create} or {@code Customer/Create}. */
public final class WebApiPartyMasterGateway implements PartyMasterGateway {

    private final VendorApiClient vendors;
    private final CustomerApiClient customers;
    private final PartyMasterWebApiClient lists;

    public WebApiPartyMasterGateway(VendorApiClient vendors, CustomerApiClient customers, PartyMasterWebApiClient lists) {
        this.vendors = vendors;
        this.customers = customers;
        this.lists = lists;
    }

    @Override
    public ApiPlatform platform() {
        return ApiPlatform.WEBAPI;
    }

    @Override
    public List<PartyClassRef> classes(PartyContext c) {
        List<PartyClassRef> all = new ArrayList<>(customers.fetchCustomerClasses(c.site(), c.ou(), c.workDate()));
        all.addAll(vendors.fetchVendorMasterClasses(c.site(), c.ou(), c.workDate()));
        return all;
    }

    @Override
    public void create(PartyClassRef cls, String name, String slId, PartyContext c) {
        if (cls.family() == PartyKind.Family.CUSTOMER) {
            Optional<CustomerApiClient.EnvCodes> env = customers.fetchEnvCodes(c.site(), c.ou(), c.workDate());
            if (env.isEmpty()) {
                throw new SkipException("Skipped due to lack of data: the environment lacks a receivable (Sundry Debtors) ledger, a trade group with a sales and a purchase term, "
                        + "a tax region or an 'Unregistered' GST category - a customer cannot be created.");
            }
            customers.createCustomer(new CustomerApiClient.CustomerSpec(name, slId, cls.code()), env.get(), c.site(), c.ou(), c.workDate());
        } else if (cls.family() == PartyKind.Family.VENDOR_MASTER) {
            VendorApiClient.EnvCodes existing = vendors.fetchEnvCodesFromExistingVendor(c.site(), c.ou(), c.workDate());
            if (existing == null) {
                throw new SkipException("Skipped due to lack of data: the Vendor Master has no party to copy the trade group, purchase term, tax region and ledger from - a "
                        + cls.name() + " cannot be created.");
            }
            // Same environment codes as an existing vendor, in the requested class.
            VendorApiClient.EnvCodes env = new VendorApiClient.EnvCodes(cls.code(), existing.glcode(), existing.fintradegrpcode(),
                    existing.purtermmaincode(), existing.admcmptaxcode(), existing.cpcategorycode(), existing.cmpcode());
            vendors.createVendor(new VendorApiClient.VendorSpec(name, slId, false, List.of()), env, 0, c.site(), c.ou(), c.workDate());
        } else {
            throw new UnsupportedOperationException("WEBAPI cannot create a party of family " + cls.family());
        }
    }

    @Override
    public boolean exists(PartyKind.Family family, String slId, PartyContext c) {
        return lists.list(family, c.site(), c.ou(), c.workDate()).stream().anyMatch(p -> slId.equals(p.getSlId()));
    }
}
