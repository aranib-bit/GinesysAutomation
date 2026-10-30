// Usage: npm run export:items -- <dbConfig> <outputTenant> [limit]
//
// <dbConfig>     - which src/config/<dbConfig>.json to connect with
// <outputTenant> - writes ../data/pools/<outputTenant>.items.json
//                  (usually the same as <dbConfig>, but kept separate since a
//                  DB config can stand in for another tenant's pool, the same
//                  way idealdb stands in for stage-ideal elsewhere in this tool)
// [limit]        - max rows to pull (default 2000) - the k6 side only ever
//                  needs as many distinct items as the largest ITEM_COUNT a
//                  run will ask for, not the tenant's entire item master.
//
// Regular (item_management_mode='I') items only for now - batch/serial pools
// are out of scope until their own HAR is captured (see the plan).

import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { getDbForTenant, closeAll } from './db.js';
import { findRegularItems } from './queries/item.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

const dbConfig = process.argv[2];
const outputTenant = process.argv[3];
const limit = Number(process.argv[4]) || 2000;

if (!dbConfig || !outputTenant) {
  console.error('Usage: npm run export:items -- <dbConfig> <outputTenant> [limit]');
  process.exit(1);
}

async function main() {
  const db = getDbForTenant(dbConfig);
  console.log(`[${dbConfig} -> ${outputTenant}] fetching up to ${limit} regular items...`);

  const rows = await findRegularItems(db, limit);
  if (rows.length === 0) {
    throw new Error(`No regular items found (item_management_mode='I' AND ext='N') via dbConfig=${dbConfig}`);
  }

  const pool = rows.map((r) => ({ itemCode: r.icode, itemType: 'I', barunit: r.barunit }));

  const outDir = join(__dirname, '..', '..', 'data', 'pools');
  mkdirSync(outDir, { recursive: true });
  const outPath = join(outDir, `${outputTenant}.items.json`);
  writeFileSync(outPath, JSON.stringify(pool, null, 2));
  console.log(`[${dbConfig} -> ${outputTenant}] wrote ${outPath} (${pool.length} items)`);
}

main()
  .catch((e) => {
    console.error(`[${dbConfig} -> ${outputTenant}] export FAILED:`, e.message);
    process.exitCode = 1;
  })
  .finally(() => closeAll());
