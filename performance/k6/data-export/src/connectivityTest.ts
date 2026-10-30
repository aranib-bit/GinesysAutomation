import { sql } from 'kysely';
import { getDbForTenant, closeAll } from './db.js';

const tenant = process.argv[2];
if (!tenant) {
  console.error('Usage: tsx src/connectivityTest.ts <tenant>');
  process.exit(1);
}

async function main() {
  const db = getDbForTenant(tenant);
  const result = await sql<{ now: string; current_database: string }>`
    SELECT now(), current_database()
  `.execute(db);
  console.log(`[${tenant}] connected ->`, result.rows[0]);

  for (const table of ['admsite', 'fingl', 'finsl', 'purservice', 'purtermmain', 'purtermdet', 'fintradegrp', 'admdocscheme']) {
    try {
      const countResult = await sql.raw<{ count: string }>(`SELECT COUNT(*) FROM ${table}`).execute(db);
      console.log(`[${tenant}] ${table}: ${countResult.rows[0].count} rows`);
    } catch (e) {
      console.log(`[${tenant}] ${table}: ERROR - ${(e as Error).message}`);
    }
  }
}

main()
  .catch((e) => {
    console.error(`[${tenant}] connection FAILED:`, e.message);
    process.exitCode = 1;
  })
  .finally(() => closeAll());
