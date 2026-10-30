package com.gsl.api.webapi.model.request.party;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.subledgerothers.FinSlGlEntry;
import com.gsl.api.model.udf.UdfValueEntry;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * The sub-ledger (finsl) record every party master create body carries: Supplier, Jobber, Agent, Transporter, Channel and Staff
 * (through {@code VendorMaster/Create}) and Customer (through {@code Customer/Create}) share these 127 keys exactly (HARs: CreateVendor*,
 * CreateJObber, CustomerCreate). Defaults are the values the UI sent for a plain party; the identity, classification and environment
 * codes have none and must be set by the caller. {@code @JsonProperty} pins the exact wire names; nulls are sent as {@code null}.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public abstract class SubLedgerCreateRequest {

    @JsonProperty("code")
    @Builder.Default
    private Long code = 0L;

    @JsonProperty("slname")
    private String slname;

    @JsonProperty("abbre")
    @Builder.Default
    private String abbre = "";

    @JsonProperty("slid")
    private String slid;

    @JsonProperty("baddr")
    @Builder.Default
    private String baddr = "";

    @JsonProperty("bdist")
    @Builder.Default
    private String bdist = "";

    @JsonProperty("bctname")
    @Builder.Default
    private String bctname = "";

    @JsonProperty("bpin")
    @Builder.Default
    private String bpin = "";

    @JsonProperty("bpH1")
    @Builder.Default
    private String bpH1 = "";

    @JsonProperty("bpH2")
    @Builder.Default
    private String bpH2 = "";

    @JsonProperty("bpH3")
    @Builder.Default
    private String bpH3 = "";

    @JsonProperty("bpH4")
    @Builder.Default
    private String bpH4 = "";

    @JsonProperty("bfX1")
    @Builder.Default
    private String bfX1 = "";

    @JsonProperty("bfX2")
    private String bfX2;

    @JsonProperty("bemail")
    @Builder.Default
    private String bemail = "";

    @JsonProperty("bwebsite")
    @Builder.Default
    private String bwebsite = "";

    @JsonProperty("bcp")
    private String bcp;

    @JsonProperty("lstno")
    @Builder.Default
    private String lstno = "";

    @JsonProperty("cstno")
    @Builder.Default
    private String cstno = "";

    @JsonProperty("clscode")
    private Long clscode;

    @JsonProperty("glcode")
    private Long glcode;

    @JsonProperty("glname")
    @Builder.Default
    private String glname = "";

    @JsonProperty("crdays")
    @Builder.Default
    private Long crdays = 0L;

    @JsonProperty("pan")
    private String pan;

    @JsonProperty("rem")
    @Builder.Default
    private String rem = "";

    @JsonProperty("defaultmarkup")
    @Builder.Default
    private Long defaultmarkup = 0L;

    @JsonProperty("roundoffmarkup")
    @Builder.Default
    private Long roundoffmarkup = 0L;

    @JsonProperty("limit")
    @Builder.Default
    private String limit = "U";

    @JsonProperty("tdssrccode")
    private Long tdssrccode;

    @JsonProperty("chqlabel")
    @Builder.Default
    private String chqlabel = "";

    @JsonProperty("whetherconsignment")
    @Builder.Default
    private String whetherconsignment = "N";

    @JsonProperty("pricetype")
    @Builder.Default
    private String pricetype = "M";

    @JsonProperty("lgtappl")
    @Builder.Default
    private String lgtappl = "N";

    @JsonProperty("defaultmarkupwsp")
    @Builder.Default
    private Long defaultmarkupwsp = 0L;

    @JsonProperty("roundoffmarkupwsp")
    @Builder.Default
    private Long roundoffmarkupwsp = 0L;

    @JsonProperty("limitwsp")
    @Builder.Default
    private String limitwsp = "U";

    @JsonProperty("saddr")
    @Builder.Default
    private String saddr = "";

    @JsonProperty("sdist")
    @Builder.Default
    private String sdist = "";

    @JsonProperty("sctname")
    @Builder.Default
    private String sctname = "";

    @JsonProperty("spin")
    @Builder.Default
    private String spin = "";

    @JsonProperty("spH1")
    @Builder.Default
    private String spH1 = "";

    @JsonProperty("spH2")
    @Builder.Default
    private String spH2 = "";

    @JsonProperty("spH3")
    @Builder.Default
    private String spH3 = "";

    @JsonProperty("spH4")
    @Builder.Default
    private String spH4 = "";

    @JsonProperty("sfX1")
    @Builder.Default
    private String sfX1 = "";

    @JsonProperty("sfX2")
    @Builder.Default
    private String sfX2 = "";

    @JsonProperty("semail")
    @Builder.Default
    private String semail = "";

    @JsonProperty("swebsite")
    @Builder.Default
    private String swebsite = "";

    @JsonProperty("scp")
    @Builder.Default
    private String scp = "";

    @JsonProperty("duedatebasis")
    @Builder.Default
    private String duedatebasis = "E";

    @JsonProperty("cashdiscountapplicable")
    @Builder.Default
    private String cashdiscountapplicable = "N";

    @JsonProperty("slowner")
    @Builder.Default
    private String slowner = "";

    @JsonProperty("brand")
    @Builder.Default
    private String brand = "";

    @JsonProperty("product")
    @Builder.Default
    private String product = "";

    @JsonProperty("companytype")
    @Builder.Default
    private String companytype = "OTH";

    @JsonProperty("industrytype")
    @Builder.Default
    private String industrytype = "OTH";

    @JsonProperty("cstdate")
    private String cstdate;

    @JsonProperty("lstdate")
    private String lstdate;

    @JsonProperty("exciseno")
    @Builder.Default
    private String exciseno = "";

    @JsonProperty("excisedate")
    private String excisedate;

    @JsonProperty("servicetaxno")
    @Builder.Default
    private String servicetaxno = "";

    @JsonProperty("servicetaxdate")
    private String servicetaxdate;

    @JsonProperty("servicetaxcategor")
    @Builder.Default
    private String servicetaxcategor = "";

    @JsonProperty("ssino")
    @Builder.Default
    private String ssino = "";

    @JsonProperty("microno")
    @Builder.Default
    private String microno = "";

    @JsonProperty("bankname")
    @Builder.Default
    private String bankname = "";

    @JsonProperty("bankaccountno")
    @Builder.Default
    private String bankaccountno = "";

    @JsonProperty("bankmicrcode")
    @Builder.Default
    private String bankmicrcode = "";

    @JsonProperty("bankifccode")
    @Builder.Default
    private String bankifccode = "";

    @JsonProperty("bankrtgscode")
    @Builder.Default
    private String bankrtgscode = "";

    @JsonProperty("bemaiL2")
    @Builder.Default
    private String bemaiL2 = "";

    @JsonProperty("permitappl")
    @Builder.Default
    private String permitappl = "N";

    @JsonProperty("purchaseglcode")
    @Builder.Default
    private Long purchaseglcode = 0L;

    @JsonProperty("purchaseglname")
    @Builder.Default
    private String purchaseglname = "";

    @JsonProperty("purchasereturnglname")
    @Builder.Default
    private String purchasereturnglname = "";

    @JsonProperty("markupsellinglimit")
    @Builder.Default
    private String markupsellinglimit = "U";

    @JsonProperty("gconsignmentglname")
    @Builder.Default
    private String gconsignmentglname = "";

    @JsonProperty("salesglname")
    @Builder.Default
    private String salesglname = "";

    @JsonProperty("salesreturnglname")
    @Builder.Default
    private String salesreturnglname = "";

    @JsonProperty("consignmentsalesglname")
    @Builder.Default
    private String consignmentsalesglname = "";

    @JsonProperty("consignmenttrnsfrglname")
    @Builder.Default
    private String consignmenttrnsfrglname = "";

    @JsonProperty("outlgtappl")
    @Builder.Default
    private String outlgtappl = "N";

    @JsonProperty("outpermitappl")
    @Builder.Default
    private String outpermitappl = "N";

    @JsonProperty("gateinappl")
    @Builder.Default
    private String gateinappl = "N";

    @JsonProperty("fintradegrpcode")
    private Long fintradegrpcode;

    @JsonProperty("purtermmaincode")
    private Long purtermmaincode;

    @JsonProperty("vatexpglname")
    @Builder.Default
    private String vatexpglname = "";

    @JsonProperty("transitglname")
    @Builder.Default
    private String transitglname = "";

    @JsonProperty("transitdiffglname")
    @Builder.Default
    private String transitdiffglname = "";

    @JsonProperty("ext")
    @Builder.Default
    private String ext = "N";

    @JsonProperty("admcmptaxcode")
    private Long admcmptaxcode;

    @JsonProperty("purformcode")
    @Builder.Default
    private Long purformcode = 0L;

    @JsonProperty("purpricechartappl")
    @Builder.Default
    private String purpricechartappl = "N";

    @JsonProperty("purdeliverybufferdays")
    @Builder.Default
    private Long purdeliverybufferdays = 0L;

    @JsonProperty("shipmenttrackingappl")
    @Builder.Default
    private String shipmenttrackingappl = "N";

    @JsonProperty("balewiselrqty")
    @Builder.Default
    private String balewiselrqty = "N";

    @JsonProperty("purduedays")
    @Builder.Default
    private Long purduedays = 1L;

    @JsonProperty("cinno")
    @Builder.Default
    private String cinno = "";

    @JsonProperty("cvrule")
    @Builder.Default
    private String cvrule = "N";

    @JsonProperty("cramt")
    private Double cramt;

    @JsonProperty("crtolerance")
    private Double crtolerance;

    @JsonProperty("croverduedays")
    private String croverduedays;

    @JsonProperty("croverdueamt")
    private Double croverdueamt;

    @JsonProperty("transitdiffslname")
    @Builder.Default
    private String transitdiffslname = "";

    @JsonProperty("transitslname")
    @Builder.Default
    private String transitslname = "";

    @JsonProperty("consignmentslname")
    @Builder.Default
    private String consignmentslname = "";

    @JsonProperty("vatexpslname")
    @Builder.Default
    private String vatexpslname = "";

    @JsonProperty("slaesslname")
    @Builder.Default
    private String slaesslname = "";

    @JsonProperty("agslname")
    @Builder.Default
    private String agslname = "";

    @JsonProperty("trpslname")
    @Builder.Default
    private String trpslname = "";

    @JsonProperty("purchasereturnslname")
    @Builder.Default
    private String purchasereturnslname = "";

    @JsonProperty("purchaseslname")
    @Builder.Default
    private String purchaseslname = "";

    @JsonProperty("consignmenttrnsfrslname")
    @Builder.Default
    private String consignmenttrnsfrslname = "";

    @JsonProperty("consignmentsalesslname")
    @Builder.Default
    private String consignmentsalesslname = "";

    @JsonProperty("salesreturnslname")
    @Builder.Default
    private String salesreturnslname = "";

    @JsonProperty("tdsapp")
    @Builder.Default
    private String tdsapp = "N";

    @JsonProperty("saletype")
    @Builder.Default
    private String saletype = "";

    @JsonProperty("salepostmethod")
    @Builder.Default
    private String salepostmethod = "";

    @JsonProperty("cpcategorycode")
    private Long cpcategorycode;

    @JsonProperty("cpgstinno")
    @Builder.Default
    private String cpgstinno = "";

    @JsonProperty("cpgstindate")
    private String cpgstindate;

    @JsonProperty("cpgstinstatecode")
    private String cpgstinstatecode;

    @JsonProperty("gstinStatus")
    @Builder.Default
    private String gstinStatus = "";

    @JsonProperty("gstinVerifiedOn")
    private String gstinVerifiedOn;

    @JsonProperty("finTDSExceptions")
    @Builder.Default
    private List<TdsException> finTDSExceptions = new java.util.ArrayList<>();

    @JsonProperty("finSLGLs")
    @Builder.Default
    private List<FinSlGlEntry> finSLGLs = new java.util.ArrayList<>();

    @JsonProperty("udfDetails")
    @Builder.Default
    private List<UdfValueEntry> udfDetails = new java.util.ArrayList<>();
}
