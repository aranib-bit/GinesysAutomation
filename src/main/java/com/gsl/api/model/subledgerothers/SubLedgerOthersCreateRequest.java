package com.gsl.api.model.subledgerothers;

import com.gsl.api.model.udf.UdfValueEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code POST /EmployeeMaster/CreateFinanceSubLedgerOther} request body — shape confirmed via
 * HAR ({@code CreateSubLedgerOthers_stage-ideal...har}). Only the fields this backfill actually
 * varies (identity fields, classification, GL linkage, UDF values) are exposed as meaningful
 * builder params; every other field defaults to the same blank/zero value the real capture sent
 * unchanged (address/contact/tax/pricing fields — none of which General Voucher's Type3
 * combination matrix cares about).
 *
 * <p><b>Open point, not resolved by either HAR:</b> {@link #glcode} (the {@code finsl.glcode}
 * "parent Sundry Debtors/Creditors GL" field) is set to the same target GL as
 * {@code finSLGLs[0].glcode} for this backfill — the real capture had these as two DIFFERENT
 * GLs (top-level 26 vs. {@code finSLGLs[0]} 2), suggesting {@link #glcode} may normally come from
 * a separate "default parent GL" lookup this backfill doesn't perform (no HAR evidence for that
 * lookup call). If the server rejects an arbitrary {@link #glcode} value, that lookup needs to be
 * added; {@code finSLGLs[0].glcode} is what actually matters for the Type3 shortfall this backfill
 * exists to fix.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubLedgerOthersCreateRequest {
    @Builder.Default
    private long code = 0;
    private String slname;
    @Builder.Default
    private String abbre = "";
    private String slid;
    @Builder.Default
    private String baddr = "";
    @Builder.Default
    private String bdist = "";
    @Builder.Default
    private String bctname = "";
    @Builder.Default
    private String bpin = "";
    @Builder.Default
    private String bpH1 = "";
    @Builder.Default
    private String bpH2 = "";
    @Builder.Default
    private String bpH3 = "";
    @Builder.Default
    private String bpH4 = "";
    @Builder.Default
    private String bfX1 = "";
    @Builder.Default
    private String bfX2 = "";
    @Builder.Default
    private String bemail = "";
    @Builder.Default
    private String bwebsite = "";
    @Builder.Default
    private String bcp = "";
    @Builder.Default
    private String lstno = "";
    @Builder.Default
    private String cstno = "";
    /** "Others" classification code — resolved live via {@code GetSubledgerClassLookup?classType=O}, never hardcoded. */
    private long clscode;
    /** See class Javadoc's "open point" note. */
    private long glcode;
    @Builder.Default
    private double agrate = 0;
    @Builder.Default
    private double intrate = 0;
    @Builder.Default
    private int crdays = 0;
    @Builder.Default
    private String pan = "";
    @Builder.Default
    private String rem = "";
    @Builder.Default
    private long taxcode = 0;
    @Builder.Default
    private double defaultmarkup = 0;
    @Builder.Default
    private double roundoffmarkup = 0;
    @Builder.Default
    private String limit = "U";
    private String tdssrccode;
    @Builder.Default
    private String chqlabel = "";
    @Builder.Default
    private long pricelistcode = 0;
    @Builder.Default
    private String pricetype = "M";
    @Builder.Default
    private long saltermcode = 0;
    @Builder.Default
    private long crcode = 0;
    @Builder.Default
    private double defaultmarkupwsp = 0;
    @Builder.Default
    private double roundoffmarkupwsp = 0;
    @Builder.Default
    private String limitwsp = "U";
    @Builder.Default
    private String saddr = "";
    @Builder.Default
    private String sdist = "";
    @Builder.Default
    private String sctname = "";
    @Builder.Default
    private String spin = "";
    @Builder.Default
    private String spH1 = "";
    @Builder.Default
    private String spH2 = "";
    @Builder.Default
    private String spH3 = "";
    @Builder.Default
    private String spH4 = "";
    @Builder.Default
    private String sfX1 = "";
    @Builder.Default
    private String sfX2 = "";
    @Builder.Default
    private String semail = "";
    @Builder.Default
    private String swebsite = "";
    @Builder.Default
    private String scp = "";
    @Builder.Default
    private String duedatebasis = "E";
    @Builder.Default
    private String companytype = "OTH";
    @Builder.Default
    private String industrytype = "OTH";
    @Builder.Default
    private String bemaiL2 = "";
    @Builder.Default
    private String markupsellinglimit = "U";
    /** Must always be "N" — never caller-settable, same extinct-ledger rationale as {@code LedgerCreateRequest}. */
    @Builder.Default
    private String ext = "N";
    @Builder.Default
    private String purpricechartappl = "N";
    @Builder.Default
    private int purduedays = 1;
    @Builder.Default
    private String cvrule = "N";
    private Double cramt;
    private Double crtolerance;
    private Integer croverduedays;
    private Double croverdueamt;
    private Long trpslcode;
    @Builder.Default
    private String trpslname = "";
    @Builder.Default
    private String tdsapp = "N";
    @Builder.Default
    private String saltremail = "";
    @Builder.Default
    private long cpcategorycode = 1;
    @Builder.Default
    private String cpgstinno = "";
    private String cpgstindate;
    @Builder.Default
    private String cpgstinstatecode = "";
    @Builder.Default
    private String channelCode = "";
    @Builder.Default
    private String transporterid = "";
    @Builder.Default
    private String gstinTaxPayerType = "";
    @Builder.Default
    private String gstinStatus = "";
    private String gstinVerifiedOn;
    @Builder.Default
    private List<Object> finTDSExceptions = List.of();
    /** The GL-permission allow-list (finsl_gl) — one entry pointing at the target GL this backfill exists to satisfy. */
    @Builder.Default
    private List<FinSlGlEntry> finSLGLs = List.of();
    @Builder.Default
    private List<UdfValueEntry> udfDetails = List.of();
}
