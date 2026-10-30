import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface Service {
  servicecode: number;
  servicename: string;
  glcode: number;
  tdstypecode: number | null;
  taxcode: number;
  invhsnsacmain_code: string;
  gst_itc_appl: string;
}

export async function findServiceByCode(db: Kysely<DB>, servicecode: number): Promise<Service> {
  const result = await sql<Service>`
    SELECT servicecode, servicename, glcode, tdstypecode, taxcode, invhsnsacmain_code, gst_itc_appl
    FROM purservice WHERE servicecode = ${servicecode} AND ext = 'N'
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No purservice row found for servicecode=${servicecode}`);
  return result.rows[0];
}

export async function findAnyService(db: Kysely<DB>): Promise<Service> {
  const result = await sql<Service>`
    SELECT servicecode, servicename, glcode, tdstypecode, taxcode, invhsnsacmain_code, gst_itc_appl
    FROM purservice WHERE ext = 'N' ORDER BY servicecode LIMIT 1
  `.execute(db);
  if (result.rows.length === 0) throw new Error('No non-deleted rows found in purservice');
  return result.rows[0];
}
