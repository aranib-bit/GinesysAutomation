import { getDbForTenant, closeAll } from './db.js';
import { assembleServiceInvoiceAdhocData } from './assembleServiceInvoiceAdhocData.js';

async function main() {
  const db = getDbForTenant('idealdb');
  const data = await assembleServiceInvoiceAdhocData(db, {
    vendorCode: 206,
    serviceCode: 8,
    siteCode: 1,
    docSchemeCode: 151,
  });
  console.log(JSON.stringify(data, null, 2));
}

main()
  .catch((e) => {
    console.error('FAILED:', e);
    process.exitCode = 1;
  })
  .finally(() => closeAll());
