import type { Kysely } from 'kysely';
import type { DB } from './types.js';
import { findVendorByCode, findAnySupplierVendor, type Vendor } from './queries/vendor.js';
import { findGlByCode } from './queries/glAccount.js';
import { findSiteByCode, type Site } from './queries/site.js';
import { findServiceByCode, findAnyService } from './queries/service.js';
import { findTradeGroupByCode } from './queries/tradeGroup.js';
import { findTermByCode, findTermChargeLines } from './queries/term.js';
import { findDocSchemeByCode, findServiceInvoiceDocScheme } from './queries/docScheme.js';
import { findHsnSacByCode, findTdsSourceByCode, findGstinByCode } from './queries/gstinAndTax.js';

// Codes that identify WHICH vendor/service/site/docScheme to use for a
// tenant - when known (e.g. stage-ideal's already-proven working
// combination), pass them in; when not (a brand-new tenant), omit and this
// falls back to "any valid row" discovery. Trade group, term, and TDS
// source are NOT independent seeds - they're derived from the chosen
// vendor's own fintradegrp_code/purtermmain_code/tdssrccode columns
// (confirmed live against idealdb - finsl carries all three directly).
export interface SeedCodes {
  vendorCode?: number;
  serviceCode?: number;
  siteCode?: number;
  docSchemeCode?: number;
}

// Charge codes 3 (CGST) and 4 (SGST) are a domain-knowledge mapping
// observed in the original captured Save payload, not something with an
// obvious schema-level label - purtermdet.chgcode doesn't self-describe
// which GST component it represents. pg returns numeric columns as strings,
// so chgcode must be coerced before comparing.
const CGST_CHGCODE = 3;
const SGST_CHGCODE = 4;

// purtermdet.rate is genuinely 0.0000 in the master table for line-level GST
// charges - the real rate is computed dynamically by the app (likely via
// invgstrate/invhsnsacslab, not yet explored) rather than stored statically
// on the term. 9% is the one real value confirmed working (from the
// original stage-ideal capture) - treat this as a placeholder needing
// per-tenant verification, not a schema-derived fact.
const FALLBACK_GST_RATE_PERCENT = 9;

function chargeGlCode(chgcode: string | number, gstin: { cgst_output_glcode: number; sgst_output_glcode: number }): number | null {
  const code = Number(chgcode);
  if (code === CGST_CHGCODE) return gstin.cgst_output_glcode;
  if (code === SGST_CHGCODE) return gstin.sgst_output_glcode;
  return null;
}

function chargeRate(chgcode: string | number, storedRate: string | number): number {
  const rate = Number(storedRate);
  if (rate > 0) return rate;
  const code = Number(chgcode);
  return code === CGST_CHGCODE || code === SGST_CHGCODE ? FALLBACK_GST_RATE_PERCENT : 0;
}

function roundAmt(baseAmount: number, ratePercent: number): number {
  return Math.round(baseAmount * (ratePercent / 100));
}

function siteObject(site: Site, gstinNo: string | null, gstinStateCode: string | null) {
  return {
    admou_Code: site.admou_code,
    code: site.code,
    ext: 'N',
    name: site.name,
    ouname: site.name,
    shrtname: site.shrtname,
    siteType: site.sitetype,
    slcode: null,
    slname: null,
    reserveInventory: 'O',
    gstinno: gstinNo,
    stateCode: gstinStateCode,
  };
}

function vendorPcodeObject(vendor: Vendor) {
  return {
    name: vendor.slname,
    slid: vendor.slid,
    namewithSLID: null,
    abbreviation: null,
    clsCode: vendor.clscode,
    agCode: null,
    class: 'Supplier',
    classType: 'S',
    glCode: vendor.glcode,
    city: null,
    state: null,
    zone: null,
    contactPerson: null,
    dueDays: null,
    ext: 'N',
    saleType: null,
    generalLedger: null,
    finSLGLExt: null,
    type: 'O',
    typeDisplay: 'Outright Sale',
    classTypeDisplay: 'Supplier',
    slCode: 0,
    tdsAppl: vendor.tds_app,
    code: vendor.slcode,
    id: `dataExportModel-${vendor.slcode}`,
  };
}

export async function assembleServiceInvoiceAdhocData(db: Kysely<DB>, seed: SeedCodes = {}) {
  const vendor = seed.vendorCode ? await findVendorByCode(db, seed.vendorCode) : await findAnySupplierVendor(db);
  const service = seed.serviceCode ? await findServiceByCode(db, seed.serviceCode) : await findAnyService(db);
  const site = await findSiteByCode(db, seed.siteCode ?? 1);
  const glAccount = await findGlByCode(db, service.glcode);
  const hsnSac = await findHsnSacByCode(db, Number(service.invhsnsacmain_code));

  // Derived from the vendor, not independently chosen - confirmed live:
  // finsl.fintradegrp_code / purtermmain_code / tdssrccode.
  const tradeGroup = await findTradeGroupByCode(db, vendor.fintradegrp_code);
  const term = await findTermByCode(db, vendor.purtermmain_code);
  const chargeLines = await findTermChargeLines(db, term.code);
  const tdsSource = vendor.tdssrccode ? await findTdsSourceByCode(db, vendor.tdssrccode) : null;

  const docScheme = seed.docSchemeCode
    ? await findDocSchemeByCode(db, seed.docSchemeCode)
    : await findServiceInvoiceDocScheme(db);

  // Owner site's GSTIN record supplies both the site's own GSTIN and the
  // CGST/SGST output GL codes used on the header charge lines - confirmed
  // live: admsite.admgstin_code -> admgstin.code.
  if (!site.admgstin_code) {
    throw new Error(`Site ${site.code} has no admgstin_code - cannot resolve GSTIN/header-charge GL codes`);
  }
  const gstin = await findGstinByCode(db, site.admgstin_code);

  const srvAmt = 7000; // fixed test amount, matching the pattern already proven in stage-ideal's data
  const lineChargeLines = chargeLines.filter((c) => c.operation_level === 'L');

  const serviceCharges = lineChargeLines.map((c, i) => {
    const rate = chargeRate(c.chgcode, c.rate);
    const code = Number(c.chgcode);
    return {
      appAmount: srvAmt,
      basis: 'P',
      chargeCode: code,
      chargeAmount: roundAmt(srvAmt, rate),
      formulae: c.formulae,
      glCode: chargeGlCode(code, gstin),
      gstComponent: code === CGST_CHGCODE ? 'CGST' : code === SGST_CHGCODE ? 'SGST' : null,
      includeInTds: c.include_in_tds,
      isReverse: 'N',
      isTax: 'Y',
      operationLevel: c.operation_level,
      rate,
      roundoffAdjAmount: null,
      seq: i + 1,
      sign: c.sign,
      slCode: null,
      source: 'G',
      isRoundOff: 'N',
    };
  });

  const headerCharges = chargeLines.map((c, i) => {
    const rate = chargeRate(c.chgcode, c.rate);
    const code = Number(c.chgcode);
    return {
      source: 'G',
      appAmount: srvAmt,
      basis: 'P',
      chargeCode: code,
      chargeName: null,
      chargeAmount: c.operation_level === 'L' ? roundAmt(srvAmt, rate) : 0,
      formulae: c.formulae,
      gLCode: chargeGlCode(code, gstin),
      gSTComponent: code === CGST_CHGCODE ? 'CGST' : code === SGST_CHGCODE ? 'SGST' : null,
      includeInTds: c.include_in_tds,
      isReverse: 'N',
      isTax: c.operation_level === 'L' ? 'Y' : 'N',
      operationLevel: c.operation_level,
      rate,
      seq: i + 1,
      sign: c.sign,
      sLCode: null,
      tagSites: [],
    };
  });

  return {
    ownerSiteCode: site.code,
    ouCode: site.admou_code,
    siteCode: site.code,
    connectedSite: String(site.code),
    connectedSiteOUCode: site.admou_code,
    connectedSiteType: site.sitetype,
    availableSites: [site.code],
    udf: { tableName: 'PURSRVMAIN', lookupData: 'true' },
    reportLayout: { menuCode: 'servinvadhoc' },
    docScheme: { menushcode: 'ServInvAdhoc', gstApplicability: '', docSchemeCode: docScheme.doccode },
    gridStateKeys: [
      'pickerwindowgrid_psiaVendor',
      'pickerwindow_psiaVendor',
      'pickerwindowgrid_serinvAdhoc_grd_servicename',
      'pickerwindow_serinvAdhoc_grd_servicename',
    ],
    vendor: {
      vendorCode: vendor.slcode,
      queryFieldName: ['name', 'slid', 'class', 'generalLedger'],
      pcode: vendorPcodeObject(vendor),
    },
    otherDetails: { ownerOuCode: site.admou_code, serviceType: 'N' },
    referenceValidation: { invoiceCode: 0 },
    ledgerTag: { glCode: service.glcode },
    calculateServiceCharges: {
      docSchemeCode: docScheme.doccode,
      tradeGroupCode: tradeGroup.code,
      termCode: term.code,
      formCode: 0,
      isExciseApplicable: false,
      exchangeRate: 1,
    },
    service: {
      serviceCode: service.servicecode,
      serviceName: service.servicename,
      glCode: service.glcode,
      hsnSacCode: hsnSac.hsn_sac_code,
      gstItcAppl: service.gst_itc_appl,
      taxCode: service.taxcode,
      srvAmt,
      serviceCharges,
    },
    headerCharges,
    tdsSourceCode: vendor.tdssrccode,
    tdsAppl: vendor.tds_app,
    gstAppl: 'N',
    ownerSite: siteObject(site, gstin.gstin_no, gstin.admgststate_code),
    refSite: siteObject(site, gstin.gstin_no, gstin.admgststate_code),
    tradeGroup: { name: tradeGroup.name, description: tradeGroup.description, ext: 'N', code: tradeGroup.code },
    term: {
      name: term.name,
      extinct: 'N',
      tradeGroupCode: term.fintradegrp_code,
      tradeGroup: tradeGroup.name,
      lineLevelIsChangeable: 'Y',
      purchaseReturnGlCode: term.purchase_return_glcode,
      purchaseGlCode: term.purchase_glcode,
      code: term.code,
    },
    tdsSource: tdsSource
      ? { tdsSrcName: tdsSource.tdssrcname, cmpCode: tdsSource.cmpcode, srcType: tdsSource.srctype, code: tdsSource.tdssrccode }
      : null,
    voucherDefaults: {
      cpGstinNo: vendor.cp_gstin_no,
      cpGstinStateCode: vendor.cp_gstin_state_code,
      ownerSiteGstinNo: gstin.gstin_no,
      ownerSiteGstinStateCode: gstin.admgststate_code,
      dueDateBasis: vendor.due_date_basis,
      creditDays: vendor.crdays,
      canModify: true,
      allowAuthorize: 'Y',
      type: 'N',
      calculateChargeOnTermForOrder: false,
    },
  };
}
