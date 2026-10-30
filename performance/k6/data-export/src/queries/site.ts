import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface Site {
  code: number;
  name: string;
  shrtname: string;
  admou_code: number | null;
  sitetype: string | null;
  admgstin_code: number | null;
  cp_gstin_no: string | null;
  cp_gstin_state_code: string | null;
}

export async function findSiteByCode(db: Kysely<DB>, code: number): Promise<Site> {
  const result = await sql<Site>`
    SELECT code, name, shrtname, admou_code, sitetype, admgstin_code, cp_gstin_no, cp_gstin_state_code
    FROM admsite WHERE code = ${code}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No admsite row found for code=${code}`);
  return result.rows[0];
}
