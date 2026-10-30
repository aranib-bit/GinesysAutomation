import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface GlAccount {
  glcode: number;
  glname: string;
  srctype: string | null;
}

// * Note: for the Service Invoice Adhoc flow, the GL code used on the line
// item comes FROM the selected service record (purservice.glcode) - this
// lookup is just to resolve glname for display/logging, not an independent
// "pick a GL" step.
export async function findGlByCode(db: Kysely<DB>, glcode: number): Promise<GlAccount> {
  const result = await sql<GlAccount>`
    SELECT glcode, glname, srctype FROM fingl WHERE glcode = ${glcode}
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No fingl row found for glcode=${glcode}`);
  return result.rows[0];
}
