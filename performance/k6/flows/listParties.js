import { Counter, Trend } from 'k6/metrics';
import { apiPost, checkEnvelope } from '../lib/http.js';
import { pathOf } from '../lib/api.js';
import * as Endpoints from '../lib/generated/endpoints.generated.js';

// Read flow for the party masters (the Vendor Master family and Customer): pages through the list the way the UI grid does, optionally
// filtered by a slid prefix. Used to measure the list screens under load, and to count what a bulk create left behind.
//   PARTY_FAMILY=VENDOR|CUSTOMER   CONNECTED_SITE=<site code>   CONNECTED_OU=<ou code>   [SLID_PREFIX=ZB...]   [PAGE_SIZE=50]
// Output line: ListParties [VU n iter m] OK family=... pages=... rows=... total=... ms=...
export const partyListRows = new Counter('party_list_rows');
export const partyListPageMs = new Trend('party_list_page_ms', true);

const FAMILY = (__ENV.PARTY_FAMILY || 'VENDOR').toUpperCase();
const ENDPOINT = { VENDOR: 'VENDOR_LIST', CUSTOMER: 'CUSTOMER_LIST' }[FAMILY];
const PAGE_SIZE = Number(__ENV.PAGE_SIZE) || 50;
const MAX_PAGES = Number(__ENV.MAX_PAGES) || 40;

function required(name) {
  const value = __ENV[name];
  if (!value) throw new Error(`Set ${name} - the party list is scoped to a site and organisation unit; refusing to guess one.`);
  return value;
}

function listBody(page, workDate) {
  const body = {
    connectedSite: required('CONNECTED_SITE'),
    connectedSiteOUCode: Number(required('CONNECTED_OU')),
    workDate,
    page,
    start: (page - 1) * PAGE_SIZE,
    limit: PAGE_SIZE,
    sort: [{ property: 'slname', direction: 'ASC' }],
  };
  const prefix = __ENV.SLID_PREFIX;
  if (prefix) body.filter = [{ property: 'slid', operator: 'like', value: prefix, condition: 'OR' }];
  return body;
}

export function runListPartiesFlow(credential) {
  if (!ENDPOINT) throw new Error(`PARTY_FAMILY must be VENDOR or CUSTOMER, got '${FAMILY}'.`);
  const endpoint = Endpoints.Party[ENDPOINT];
  const workDate = `${new Date().toISOString().slice(0, 10)}T00:00:00`;
  const started = Date.now();
  let rows = 0;
  let total = 0;
  let pages = 0;
  for (let page = 1; page <= MAX_PAGES; page++) {
    const res = apiPost(credential, pathOf(endpoint), listBody(page, workDate), null, `Party.${ENDPOINT}`);
    partyListPageMs.add(res.timings.duration);
    if (!checkEnvelope(res, 'list parties')) {
      console.log(`ListParties [VU ${__VU} iter ${__ITER}] FAIL family=${FAMILY} page=${page} status=${res.status} body=${String(res.body).slice(0, 300)}`);
      return;
    }
    const result = JSON.parse(res.body).result || {};
    const items = result.items || [];
    pages++;
    rows += items.length;
    total = result.totalCount || total;
    if (items.length === 0 || rows >= total) break;
  }
  partyListRows.add(rows);
  console.log(`ListParties [VU ${__VU} iter ${__ITER}] OK family=${FAMILY} pages=${pages} rows=${rows} total=${total} ms=${Date.now() - started}`);
}
