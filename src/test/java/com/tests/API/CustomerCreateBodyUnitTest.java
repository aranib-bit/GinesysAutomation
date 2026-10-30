package com.tests.API;

import com.gsl.api.client.CustomerApiClient;
import com.gsl.utils.Faker.party.Completeness;
import com.gsl.utils.Faker.party.IndianIdentifiers;
import com.gsl.utils.Faker.party.IndianPartyGenerator;
import com.gsl.utils.Faker.party.PartyProfile;
import com.gsl.api.webapi.model.request.party.CustomerCreateRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * The {@code Customer/Create} request the framework builds: only the name/id, PAN, class, environment codes and UDF values vary; everything
 * else is the POJO default (proven equal to the HAR body in {@link PartyCreateWireTest}).
 */
public class CustomerCreateBodyUnitTest {

    private static PartyProfile profile(Completeness level) {
        return new IndianPartyGenerator(new java.util.Random(7)).next(level);
    }

    private static final List<com.gsl.api.common.CityRef> CITIES = List.of(new com.gsl.api.common.CityRef("KOLKATA", "WEST BENGAL", "INDIA"));

    private static final CustomerApiClient.EnvCodes ENV = new CustomerApiClient.EnvCodes(14, 1, 1015, 1, 55L, "M", 4, 1, 2L);

    @Test(description = "Varying fields are applied; constants stay as captured")
    public void bodyMatchesHar() {
        CustomerCreateRequest b = CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec("ZZ C1", "ZC1", 2), ENV, List.of(), profile(Completeness.TYPICAL), CITIES);

        Assert.assertEquals(b.getSlname(), "ZZ C1");
        Assert.assertEquals(b.getSlid(), "ZC1");
        Assert.assertEquals(b.getClscode().longValue(), 2L);
        Assert.assertEquals(b.getGlcode().longValue(), 14L);
        Assert.assertEquals(b.getFintradegrpcode().longValue(), 1L);
        Assert.assertEquals(b.getSaltradegrpcode().longValue(), 1L);
        Assert.assertEquals(b.getSaltermcode().longValue(), 1015L);
        Assert.assertEquals(b.getPurtermmaincode().longValue(), 1L);
        Assert.assertEquals(b.getPricelistcode().longValue(), 55L);
        Assert.assertEquals(b.getPricetype(), "M");
        Assert.assertEquals(b.getAdmcmptaxcode().longValue(), 4L);
        Assert.assertEquals(b.getCpcategorycode().longValue(), 1L);
        Assert.assertEquals(b.getSaletype(), "O");

        Assert.assertEquals(b.getTdsapp(), "N");
        Assert.assertNull(b.getTdssrccode());
        Assert.assertEquals(b.getFinTDSExceptions().size(), 0);
        Assert.assertEquals(b.getLimit(), "U");
        Assert.assertEquals(b.getDuedatebasis(), "E");
        Assert.assertEquals(b.getCompanytype(), "OTH");
        Assert.assertEquals(b.getExt(), "N");
    }

    @Test(description = "The generated profile supplies contact, address and PAN; name and id stay the caller's")
    public void profileDataIsApplied() {
        PartyProfile p = profile(Completeness.TYPICAL);
        CustomerCreateRequest b = CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec("ZZ C2", "ZC2", 1), ENV, List.of(), p, CITIES);
        Assert.assertEquals(b.getSlname(), "ZZ C2");
        Assert.assertEquals(b.getSlid(), "ZC2");
        Assert.assertEquals(b.getPan(), p.pan());
        Assert.assertTrue(IndianIdentifiers.isValidPan(b.getPan()));
        Assert.assertEquals(b.getBcp(), p.contactPerson());
        Assert.assertEquals(b.getBfX2(), p.mobile());
        Assert.assertEquals(b.getBaddr(), p.address());
        Assert.assertEquals(b.getBpin(), p.pin());
        Assert.assertEquals(b.getBemail(), p.email());
        Assert.assertEquals(b.getBctname(), "KOLKATA", "the city comes from the city master, never from the generator");
        Assert.assertEquals(b.getCpcategorycode().longValue(), 1L, "TYPICAL has no GSTIN: stays Unregistered");
        Assert.assertEquals(b.getCpgstinno(), "");
    }

    @Test(description = "A FULL profile's GSTIN is sent with the Normal Registered category; without that category it is dropped")
    public void gstinNeedsTheRegisteredCategory() {
        PartyProfile full = profile(Completeness.FULL);
        CustomerCreateRequest registered = CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec("A", "ZC4", 1), ENV, List.of(), full, CITIES);
        Assert.assertEquals(registered.getCpgstinno(), full.gstin());
        Assert.assertEquals(registered.getCpgstinstatecode(), full.gstStateCode());
        Assert.assertEquals(registered.getCpcategorycode().longValue(), 2L);
        Assert.assertEquals(registered.getBankname(), full.bankName());

        CustomerApiClient.EnvCodes noRegistered = new CustomerApiClient.EnvCodes(14, 1, 1015, 1, 55L, "M", 4, 1, null);
        CustomerCreateRequest plain = CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec("A", "ZC5", 1), noRegistered, List.of(), full, CITIES);
        Assert.assertEquals(plain.getCpgstinno(), "");
        Assert.assertEquals(plain.getCpcategorycode().longValue(), 1L);
    }

    @Test(description = "No sale price list in the environment: the price list is left unset")
    public void noPriceList() {
        CustomerApiClient.EnvCodes env = new CustomerApiClient.EnvCodes(14, 1, 1015, 1, null, "M", 4, 1, 2L);
        Assert.assertNull(CustomerApiClient.buildCreateRequest(new CustomerApiClient.CustomerSpec("A", "ZC3", 1), env, List.of(), profile(Completeness.TYPICAL), CITIES).getPricelistcode());
    }
}
