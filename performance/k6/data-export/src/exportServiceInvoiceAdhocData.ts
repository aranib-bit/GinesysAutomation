// Usage: npm run export -- <dbConfig> <outputTenant>
//
// <dbConfig>     - which src/config/<dbConfig>.json to connect with
// <outputTenant> - writes ../data/business/<outputTenant>.serviceInvoiceAdhoc.json
//                  (usually the same as <dbConfig>, but kept separate since
//                  a DB config can be a stand-in for validation, as idealdb
//                  was for stage-ideal - see the plan for context)
//
// Optional: src/config/seed/<outputTenant>.json can pin specific
// vendor/service/site/docScheme codes (see SeedCodes in
// assembleServiceInvoiceAdhocData.ts); omit any field to fall back to
// "first valid row" discovery.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { getDbForTenant, closeAll } from './db.js';
import { assembleServiceInvoiceAdhocData, type SeedCodes } from './assembleServiceInvoiceAdhocData.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

const dbConfig = process.argv[2];
const outputTenant = process.argv[3];
if (!dbConfig || !outputTenant) {
  console.error('Usage: npm run export -- <dbConfig> <outputTenant>');
  process.exit(1);
}

function loadSeed(tenant: string): SeedCodes {
  const path = join(__dirname, 'config', 'seed', `${tenant}.json`);
  if (!existsSync(path)) return {};
  return JSON.parse(readFileSync(path, 'utf-8'));
}

async function main() {
  const db = getDbForTenant(dbConfig);
  const seed = loadSeed(outputTenant);
  console.log(`[${dbConfig} -> ${outputTenant}] assembling with seed:`, seed);

  const data = await assembleServiceInvoiceAdhocData(db, seed);

  const outPath = join(__dirname, '..', '..', 'data', 'business', `${outputTenant}.serviceInvoiceAdhoc.json`);
  writeFileSync(outPath, JSON.stringify(data, null, 2));
  console.log(`[${dbConfig} -> ${outputTenant}] wrote ${outPath}`);
  console.log(`  vendor=${data.vendor.vendorCode} service=${data.service.serviceCode} site=${data.siteCode} docScheme=${data.docScheme.docSchemeCode}`);
}

main()
  .catch((e) => {
    console.error(`[${dbConfig} -> ${outputTenant}] export FAILED:`, e.message);
    process.exitCode = 1;
  })
  .finally(() => closeAll());
