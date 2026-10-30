import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface RegularItemRow {
  icode: string;
  barunit: string;
}

// Matches the exact filter given for the k6 regular-item pool: item_management_mode
// 'I' is a Regular (non-batch, non-serial) item, ext='N' means not deleted/extinct.
// Deliberately no ORDER BY - the pool just needs a stable-enough set of valid
// regular item codes, not a specific ordering.
export async function findRegularItems(db: Kysely<DB>, limit: number): Promise<RegularItemRow[]> {
  const result = await sql<RegularItemRow>`
    SELECT icode, barunit FROM invitem WHERE item_management_mode = 'I' AND ext = 'N' LIMIT ${limit}
  `.execute(db);
  return result.rows;
}
