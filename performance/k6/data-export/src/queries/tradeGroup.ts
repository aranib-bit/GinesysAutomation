import { sql } from 'kysely';
import type { Kysely } from 'kysely';
import type { DB } from '../types.js';

export interface TradeGroup {
  code: number;
  name: string;
  description: string | null;
}

export async function findTradeGroupByCode(db: Kysely<DB>, code: number): Promise<TradeGroup> {
  const result = await sql<TradeGroup>`
    SELECT code, name, description FROM fintradegrp WHERE code = ${code} AND ext = 'N'
  `.execute(db);
  if (result.rows.length === 0) throw new Error(`No fintradegrp row found for code=${code}`);
  return result.rows[0];
}
