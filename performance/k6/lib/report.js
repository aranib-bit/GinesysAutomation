import { TENANT } from './config.js';

function formatMs(v) {
  return v === undefined ? 'n/a' : `${v.toFixed(2)}ms`;
}

function buildTextSummary(data) {
  const m = data.metrics;
  const lines = [`--- k6 summary [${TENANT}] ---`];
  if (m.http_reqs) lines.push(`http_reqs: ${m.http_reqs.values.count}`);
  if (m.http_req_duration) {
    lines.push(
      `http_req_duration: avg=${formatMs(m.http_req_duration.values.avg)} ` +
        `p95=${formatMs(m.http_req_duration.values['p(95)'])} max=${formatMs(m.http_req_duration.values.max)}`
    );
  }
  if (m.http_req_failed) lines.push(`http_req_failed rate: ${(m.http_req_failed.values.rate * 100).toFixed(2)}%`);
  if (m.checks) lines.push(`checks pass rate: ${(m.checks.values.rate * 100).toFixed(2)}%`);
  lines.push(`(full metrics: reports/${TENANT}/summary.json, reports/${TENANT}/summary.html)`);
  return lines.join('\n');
}

function buildHtmlReport(data) {
  const m = data.metrics;
  const rows = Object.keys(m)
    .sort()
    .map((name) => {
      const v = m[name].values;
      const cells = Object.keys(v)
        .map((k) => `<td>${k}</td><td>${typeof v[k] === 'number' ? v[k].toFixed(2) : v[k]}</td>`)
        .join('');
      return `<tr><th colspan="2">${name}</th></tr><tr>${cells}</tr>`;
    })
    .join('\n');
  return `<!doctype html>
<html><head><meta charset="utf-8"><title>k6 Summary - ${TENANT}</title>
<style>
body{font-family:sans-serif;margin:2rem;}
table{border-collapse:collapse;width:100%;}
td,th{border:1px solid #ccc;padding:4px 8px;text-align:left;font-size:0.9rem;}
th{background:#f2f2f2;}
</style></head>
<body>
<h1>k6 Run Summary - ${TENANT}</h1>
<p>Generated: ${new Date().toISOString()}</p>
<table>${rows}</table>
</body></html>`;
}

export function handleSummary(data) {
  return {
    stdout: buildTextSummary(data),
    [`reports/${TENANT}/summary.json`]: JSON.stringify(data, null, 2),
    [`reports/${TENANT}/summary.html`]: buildHtmlReport(data),
  };
}
