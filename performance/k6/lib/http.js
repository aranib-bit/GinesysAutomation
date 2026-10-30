import http from 'k6/http';
import { check } from 'k6';
import { baseUrl, TENANT, tenancyMode, uiPath } from './config.js';
import { authHeaders, authMode } from './auth.js';

export function cacheBuster() {
  return Date.now();
}

export function sessionId() {
  return `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
}

function correlationId() {
  return Math.random().toString(16).slice(2, 14);
}

// Full browser-realistic header set (DIAGNOSTIC: testing whether
// ItemMaster/Create's 500 needs these - k6's default 4-header set never
// carried Origin/Referer/User-Agent/sec-fetch-*, and the isolated raw-HTTP
// test that added them skipped the realistic call sequence, so the
// combination of "real sequence" + "real headers" was never actually tested
// together until now).
function commonHeaders(credential) {
  return Object.assign(
    {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      Accept: '*/*',
      'Accept-Language': 'en-US,en;q=0.9',
      Origin: baseUrl,
      Referer: `${baseUrl}${uiPath}`,
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36',
      'sec-ch-ua': '"Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"',
      'sec-ch-ua-mobile': '?0',
      'sec-ch-ua-platform': '"Windows"',
      'sec-fetch-dest': 'empty',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'same-origin',
      'x-correlation-id': correlationId(),
    },
    authHeaders(credential)
  );
}

function toQueryString(params) {
  return Object.keys(params)
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&');
}

function buildUrl(path, queryParams) {
  if (!queryParams) return `${baseUrl}${path}`;
  const qs = toQueryString(queryParams);
  return qs ? `${baseUrl}${path}?${qs}` : `${baseUrl}${path}`;
}

// Set -e DEBUG_HTTP=true to log every request/response (truncated) via
// console.log - pair with `k6 run --console-output=reports/console.log` so
// the raw API traffic ends up as a file alongside the HTML/JSON report,
// since k6 doesn't let handleSummary() see per-request bodies.
function debugLog(method, url, reqBody, res) {
  if (!__ENV.DEBUG_HTTP) return;
  const resBody = res.body && res.body.length > 4000 ? `${res.body.slice(0, 4000)}...TRUNCATED` : res.body;
  console.log(
    `[HTTP] ${method} ${url} -> ${res.status}${reqBody ? `\n  req: ${reqBody}` : ''}\n  res: ${resBody}`
  );
}

export function apiGet(credential, path, queryParams, endpointTag) {
  const url = buildUrl(path, queryParams);
  const res = http.get(url, {
    headers: commonHeaders(credential),
    tags: { endpoint: endpointTag || path, authMode, tenant: TENANT, tenancyMode },
  });
  debugLog('GET', url, null, res);
  return res;
}

export function apiPost(credential, path, body, queryParams, endpointTag) {
  const url = buildUrl(path, queryParams);
  const jsonBody = JSON.stringify(body || {});
  const res = http.post(url, jsonBody, {
    headers: commonHeaders(credential),
    tags: { endpoint: endpointTag || path, authMode, tenant: TENANT, tenancyMode },
  });
  debugLog('POST', url, jsonBody, res);
  return res;
}

// PUT and DELETE with a JSON body (voucher revert/release are PUT, voucher delete is DELETE with a body) - same headers/tags as apiPost.
export function apiPut(credential, path, body, queryParams, endpointTag) {
  const url = buildUrl(path, queryParams);
  const jsonBody = JSON.stringify(body || {});
  const res = http.put(url, jsonBody, {
    headers: commonHeaders(credential),
    tags: { endpoint: endpointTag || path, authMode, tenant: TENANT, tenancyMode },
  });
  debugLog('PUT', url, jsonBody, res);
  return res;
}

export function apiDelete(credential, path, body, queryParams, endpointTag) {
  const url = buildUrl(path, queryParams);
  const jsonBody = JSON.stringify(body || {});
  const res = http.del(url, jsonBody, {
    headers: commonHeaders(credential),
    tags: { endpoint: endpointTag || path, authMode, tenant: TENANT, tenancyMode },
  });
  debugLog('DELETE', url, jsonBody, res);
  return res;
}

// Fires multiple independent GET/POST calls concurrently via k6's http.batch()
// - same headers/tags/shapes as apiGet/apiPost, just not run one-at-a-time.
// Used for per-item lookups (item detail, batch/serial validation) when a
// GRC has many distinct items: this only changes *concurrency*, not what's
// sent, so it stays faithful to a single-item capture replicated N times
// while keeping large-N iterations practical.
// Multipart/form-data upload - added for General Voucher's attachment upload
// (WebAPI/Utility/UploadedDocAttachment), the first real file-upload endpoint
// exercised in this repo (createItem.js's imageData is a base64 string
// embedded in a normal JSON body, not a real multipart upload). `fields` is a
// plain object whose values are either plain strings or a k6 http.file(...)
// handle - passing an object (not a string) as the body tells k6 to encode
// it as multipart/form-data itself, boundary and all, so the hardcoded
// Content-Type: application/json from commonHeaders() has to be removed
// first or k6's own multipart Content-Type would be overridden by it.
export function apiPostMultipart(credential, path, fields, endpointTag) {
  const url = `${baseUrl}${path}`;
  const headers = commonHeaders(credential);
  delete headers['Content-Type'];
  const res = http.post(url, fields, {
    headers,
    tags: { endpoint: endpointTag || path, authMode, tenant: TENANT, tenancyMode },
  });
  debugLog('POST', url, JSON.stringify(Object.keys(fields)), res);
  return res;
}

export function apiBatch(credential, requests) {
  const headers = commonHeaders(credential);
  const batchRequests = requests.map((r) => {
    const url = buildUrl(r.path, r.queryParams);
    const params = { headers, tags: { endpoint: r.endpointTag || r.path, authMode, tenant: TENANT, tenancyMode } };
    return r.method === 'GET' ? { method: 'GET', url, params } : { method: 'POST', url, params, body: JSON.stringify(r.body || {}) };
  });
  const responses = http.batch(batchRequests);
  responses.forEach((res, i) => debugLog(requests[i].method, batchRequests[i].url, batchRequests[i].body, res));
  return responses;
}

export function checkEnvelope(res, label, extraChecks) {
  const checks = Object.assign(
    {
      [`${label}: status 200`]: (r) => r.status === 200,
      [`${label}: success true`]: (r) => {
        try {
          return JSON.parse(r.body).success === true;
        } catch (e) {
          return false;
        }
      },
    },
    extraChecks || {}
  );
  return check(res, checks);
}
