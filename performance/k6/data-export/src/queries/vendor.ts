import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface Vendor {
  slcode: number;
  slid: string;
  slname: string;
  glcode: number;
  clscode: number;
  cmpcode: number;
  tds_app: string | null;
  tdssrccode: number | null;
  fintradegrp_code: number;
  purtermmain_code: number;
  cp_gstin_no: string | null;
  cp_gstin_state_code: string | null;
  due_date_basis: string;
  crdays: number;
}

const SELECT_COLUMNS = `
  slcode, slid, slname, glcode, clscode, cmpcode, tds_app, tdssrccode,
  fintradegrp_code, purtermmain_code, cp_gstin_no, cp_gstin_state_code,
  due_date_basis, crdays
`;

const SELECT_COLUMNS_FS = `
  fs.slcode, fs.slid, fs.slname, fs.glcode, fs.clscode, fs.cmpcode, fs.tds_app, fs.tdssrccode,
  fs.fintradegrp_code, fs.purtermmain_code, fs.cp_gstin_no, fs.cp_gstin_state_code,
  fs.due_date_basis, fs.crdays
`;

// finsl.clscode -> admcls.clscode; admcls.clsname holds a human-readable
// name ("Supplier"/"Customer"); ext='N' means not deleted/extinct - all
// confirmed via the Selenium project's AdmClsQuery.java and verified live
// against idealdb (the real stage-ideal DB). The vendor's own trade group,
// term, and TDS source are all FK columns directly on finsl - they're
// derived from the vendor, not chosen independently.
export async function findVendorByCode(db: Kysely<DB>, slcode: number): Promise<Vendor> {
  const result = await sql<Vendor>`
    SELECT ${sql.raw(SELECT_COLUMNS)} FROM finsl WHERE slcode = ${slcode}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No finsl row found for slcode=${slcode}`);
  return result.rows[0];
}

export async function findAnySupplierVendor(db: Kysely<DB>): Promise<Vendor> {
  const result = await sql<Vendor>`
    SELECT ${sql.raw(SELECT_COLUMNS_FS)}
    FROM finsl fs
    JOIN admcls ac ON ac.clscode = fs.clscode
    WHERE ac.clsname ILIKE '%supplier%' AND fs.ext = 'N'
    ORDER BY fs.slcode
    LIMIT 1
  `.execute(db);
  if (result.rows.length === 0) {
    throw new Error("No Supplier-classed rows found in finsl (joined via admcls.clsname ILIKE '%supplier%')");
  }
  return result.rows[0];
}
