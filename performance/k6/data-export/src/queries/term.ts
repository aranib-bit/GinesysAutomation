import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface Term {
  code: number;
  name: string;
  fintradegrp_code: number;
  purchase_glcode: number;
  purchase_return_glcode: number;
}

export interface TermChargeLine {
  purtermmain_code: number;
  seq: number;
  chgcode: number;
  rate: string;
  formulae: string;
  sign: string;
  operation_level: string;
  code: number;
  include_in_tds: string;
}

export async function findTermByCode(db: Kysely<DB>, code: number): Promise<Term> {
  const result = await sql<Term>`
    SELECT code, name, fintradegrp_code, purchase_glcode, purchase_return_glcode
    FROM purtermmain WHERE code = ${code} AND ext = 'N'
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No purtermmain row found for code=${code}`);
  return result.rows[0];
}

// Charge-line rules (e.g. CGST/SGST rate + formula) attached to a term -
// the master-data equivalent of the headerCharges array captured from the
// live CalculateServiceCharges response in the original HAR-based data.
export async function findTermChargeLines(db: Kysely<DB>, purtermmain_code: number): Promise<TermChargeLine[]> {
  const result = await sql<TermChargeLine>`
    SELECT purtermmain_code, seq, chgcode, rate, formulae, sign, operation_level, code, include_in_tds
    FROM purtermdet WHERE purtermmain_code = ${purtermmain_code} ORDER BY seq
  `.execute(db);
  return result.rows;
}
