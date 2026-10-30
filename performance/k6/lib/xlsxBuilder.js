// Generic, domain-free .xlsx writer for k6 VUs.
//
// k6 cannot write files and has no zip/xlsx library, but an upload endpoint
// only needs the file's bytes - so a VU can build its own workbook in memory
// and hand the resulting ArrayBuffer straight to http.file(). This module
// emits a minimal OOXML package (shared-strings based, one worksheet, one
// date style) and packs it with an uncompressed ("stored") zip + CRC32, which
// every xlsx reader accepts.
//
// Knows nothing about what the data means - callers pass column definitions
// and row values only.
//
//   columns: [{ name: 'REFERENCE_NO', kind: 'string' | 'number' | 'date' }]
//   rows:    array of arrays, one value per column; null/undefined/'' => blank cell
//            ('date' values are Excel serial numbers)

const XML_HEAD = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n';

function xmlEscape(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function columnLetter(index) {
  let n = index + 1;
  let s = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    s = String.fromCharCode(65 + rem) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

function utf8Bytes(str) {
  const out = [];
  for (let i = 0; i < str.length; i++) {
    let c = str.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length) {
      const d = str.charCodeAt(i + 1);
      if (d >= 0xdc00 && d <= 0xdfff) {
        c = 0x10000 + ((c - 0xd800) << 10) + (d - 0xdc00);
        i++;
      }
    }
    if (c < 0x80) out.push(c);
    else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
    else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
  }
  return Uint8Array.from(out);
}

const CRC_TABLE = (function () {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(bytes) {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

// entries: [{ name, bytes }] -> ArrayBuffer (zip, method 0 = stored)
function zipStored(entries) {
  const nameBytes = entries.map((e) => utf8Bytes(e.name));
  const crcs = entries.map((e) => crc32(e.bytes));

  let size = 22;
  entries.forEach((e, i) => {
    size += 30 + nameBytes[i].length + e.bytes.length + 46 + nameBytes[i].length;
  });

  const buf = new Uint8Array(size);
  const dv = new DataView(buf.buffer);
  let p = 0;
  const offsets = [];

  entries.forEach((e, i) => {
    offsets.push(p);
    dv.setUint32(p, 0x04034b50, true);
    dv.setUint16(p + 4, 20, true); // version needed
    dv.setUint16(p + 6, 0x0800, true); // UTF-8 names
    dv.setUint16(p + 8, 0, true); // stored
    dv.setUint16(p + 10, 0, true); // time
    dv.setUint16(p + 12, 0x21, true); // date (1980-01-01)
    dv.setUint32(p + 14, crcs[i], true);
    dv.setUint32(p + 18, e.bytes.length, true);
    dv.setUint32(p + 22, e.bytes.length, true);
    dv.setUint16(p + 26, nameBytes[i].length, true);
    dv.setUint16(p + 28, 0, true);
    p += 30;
    buf.set(nameBytes[i], p);
    p += nameBytes[i].length;
    buf.set(e.bytes, p);
    p += e.bytes.length;
  });

  const cdStart = p;
  entries.forEach((e, i) => {
    dv.setUint32(p, 0x02014b50, true);
    dv.setUint16(p + 4, 20, true); // version made by
    dv.setUint16(p + 6, 20, true); // version needed
    dv.setUint16(p + 8, 0x0800, true);
    dv.setUint16(p + 10, 0, true);
    dv.setUint16(p + 12, 0, true);
    dv.setUint16(p + 14, 0x21, true);
    dv.setUint32(p + 16, crcs[i], true);
    dv.setUint32(p + 20, e.bytes.length, true);
    dv.setUint32(p + 24, e.bytes.length, true);
    dv.setUint16(p + 28, nameBytes[i].length, true);
    dv.setUint16(p + 30, 0, true);
    dv.setUint16(p + 32, 0, true);
    dv.setUint16(p + 34, 0, true);
    dv.setUint16(p + 36, 0, true);
    dv.setUint32(p + 38, 0, true);
    dv.setUint32(p + 42, offsets[i], true);
    p += 46;
    buf.set(nameBytes[i], p);
    p += nameBytes[i].length;
  });
  const cdSize = p - cdStart;

  dv.setUint32(p, 0x06054b50, true);
  dv.setUint16(p + 4, 0, true);
  dv.setUint16(p + 6, 0, true);
  dv.setUint16(p + 8, entries.length, true);
  dv.setUint16(p + 10, entries.length, true);
  dv.setUint32(p + 12, cdSize, true);
  dv.setUint32(p + 16, cdStart, true);
  dv.setUint16(p + 20, 0, true);

  return buf.buffer;
}

const CONTENT_TYPES =
  XML_HEAD +
  '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
  '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
  '<Default Extension="xml" ContentType="application/xml"/>' +
  '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
  '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' +
  '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
  '<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>' +
  '</Types>';

const ROOT_RELS =
  XML_HEAD +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
  '</Relationships>';

const WORKBOOK_RELS =
  XML_HEAD +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>' +
  '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' +
  '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>' +
  '</Relationships>';

// xf 0 = general, xf 1 = built-in date format 14 (m/d/yyyy)
const STYLES =
  XML_HEAD +
  '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
  '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>' +
  '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>' +
  '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
  '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
  '<cellXfs count="2">' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
  '<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>' +
  '</cellXfs>' +
  '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>' +
  '</styleSheet>';

export function buildXlsx({ sheetName, columns, rows }) {
  const sstIndex = {};
  const sstList = [];
  let sstRefs = 0;
  const sst = (value) => {
    sstRefs++;
    const key = String(value);
    if (sstIndex[key] === undefined) {
      sstIndex[key] = sstList.length;
      sstList.push(key);
    }
    return sstIndex[key];
  };

  const letters = columns.map((_, i) => columnLetter(i));
  const lastCol = letters[letters.length - 1];
  const parts = [];

  parts.push(
    XML_HEAD +
      '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
      `<dimension ref="A1:${lastCol}${rows.length + 1}"/>` +
      '<sheetData>'
  );

  // header row
  let hdr = '<row r="1">';
  columns.forEach((c, i) => {
    hdr += `<c r="${letters[i]}1" t="s"><v>${sst(c.name)}</v></c>`;
  });
  parts.push(hdr + '</row>');

  for (let r = 0; r < rows.length; r++) {
    const rowNo = r + 2;
    const values = rows[r];
    let row = `<row r="${rowNo}">`;
    for (let i = 0; i < columns.length; i++) {
      const v = values[i];
      if (v === null || v === undefined || v === '') continue;
      const ref = letters[i] + rowNo;
      const kind = columns[i].kind;
      if (kind === 'number') row += `<c r="${ref}"><v>${v}</v></c>`;
      else if (kind === 'date') row += `<c r="${ref}" s="1"><v>${v}</v></c>`;
      else row += `<c r="${ref}" t="s"><v>${sst(v)}</v></c>`;
    }
    parts.push(row + '</row>');
  }
  parts.push('</sheetData></worksheet>');

  const sharedStrings =
    XML_HEAD +
    `<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sstRefs}" uniqueCount="${sstList.length}">` +
    sstList.map((s) => `<si><t xml:space="preserve">${xmlEscape(s)}</t></si>`).join('') +
    '</sst>';

  const workbook =
    XML_HEAD +
    '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
    `<sheets><sheet name="${xmlEscape((sheetName || 'Sheet1').slice(0, 31))}" sheetId="1" r:id="rId1"/></sheets>` +
    '</workbook>';

  return zipStored([
    { name: '[Content_Types].xml', bytes: utf8Bytes(CONTENT_TYPES) },
    { name: '_rels/.rels', bytes: utf8Bytes(ROOT_RELS) },
    { name: 'xl/workbook.xml', bytes: utf8Bytes(workbook) },
    { name: 'xl/_rels/workbook.xml.rels', bytes: utf8Bytes(WORKBOOK_RELS) },
    { name: 'xl/styles.xml', bytes: utf8Bytes(STYLES) },
    { name: 'xl/sharedStrings.xml', bytes: utf8Bytes(sharedStrings) },
    { name: 'xl/worksheets/sheet1.xml', bytes: utf8Bytes(parts.join('')) },
  ]);
}
