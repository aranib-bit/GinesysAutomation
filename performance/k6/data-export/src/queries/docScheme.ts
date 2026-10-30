import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface DocScheme {
  doccode: number;
  docname: string;
  prefix: string | null;
  suffix: string | null;
  sample_docno: string | null;
  gstappl: string;
}

export async function findDocSchemeByCode(db: Kysely<DB>, doccode: number): Promise<DocScheme> {
  const result = await sql<DocScheme>`
    SELECT doccode, docname, prefix, suffix, sample_docno, gstappl
    FROM admdocscheme WHERE doccode = ${doccode} AND ext = 'N'
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No admdocscheme row found for doccode=${doccode}`);
  return result.rows[0];
}

// Fallback for a brand-new tenant with no known doc scheme code yet:
// admdocscheme has no menu-association column in this schema (the
// RT.GetAdmSchemeDocList API result seen in the HAR is presumably filtered
// via a separate mapping not yet located), so this searches by name instead
// - "Service Invoice" is confirmed to be doccode 151 in idealdb via this
// exact match. Not guaranteed for every tenant; verify the result.
export async function findServiceInvoiceDocScheme(db: Kysely<DB>): Promise<DocScheme> {
  const result = await sql<DocScheme>`
    SELECT doccode, docname, prefix, suffix, sample_docno, gstappl
    FROM admdocscheme WHERE docname ILIKE '%service invoice%' AND ext = 'N'
    ORDER BY doccode LIMIT 1
  `.execute(db);
  if (result.rows.length === 0) throw new Error("No admdocscheme row found matching docname ILIKE '%service invoice%'");
  return result.rows[0];
}
