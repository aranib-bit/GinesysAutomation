import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface HsnSac {
  code: number;
  hsn_sac_code: string;
  description: string;
}

export async function findHsnSacByCode(db: Kysely<DB>, code: number): Promise<HsnSac> {
  const result = await sql<HsnSac>`
    SELECT code, hsn_sac_code, description FROM invhsnsacmain WHERE code = ${code}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No invhsnsacmain row found for code=${code}`);
  return result.rows[0];
}

export interface TdsSource {
  tdssrccode: number;
  tdssrcname: string;
  srctype: string;
  cmpcode: number;
}

export async function findTdsSourceByCode(db: Kysely<DB>, tdssrccode: number): Promise<TdsSource> {
  const result = await sql<TdsSource>`
    SELECT tdssrccode, tdssrcname, srctype, cmpcode FROM fintdssrc WHERE tdssrccode = ${tdssrccode}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No fintdssrc row found for tdssrccode=${tdssrccode}`);
  return result.rows[0];
}

export interface Gstin {
  code: number;
  admou_code: number;
  gstin_no: string;
  admgststate_code: string;
  cgst_output_glcode: number;
  sgst_output_glcode: number;
  igst_output_glcode: number;
}

export async function findGstinByCode(db: Kysely<DB>, code: number): Promise<Gstin> {
  const result = await sql<Gstin>`
    SELECT code, admou_code, gstin_no, admgststate_code, cgst_output_glcode, sgst_output_glcode, igst_output_glcode
    FROM admgstin WHERE code = ${code}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No admgstin row found for code=${code}`);
  return result.rows[0];
}
