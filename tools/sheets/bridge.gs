/**
 * SICIP QA Visit — Google Sheets bridge (Milestone E)
 *
 * One-way mirror: Supabase DB -> this spreadsheet. DB always wins; re-running
 * syncAll() wipes and rewrites the three tabs below from scratch. Any other
 * tab in this spreadsheet is left untouched.
 *
 * Setup: see tools/sheets/SETUP.md. Entry point for the time-driven trigger
 * is syncAll().
 */

// Fixed 17-code ladder, in CATEGORIES.md order. Breakdown tab columns.
var CATEGORY_CODES = ['A***', 'A**+', 'A**', 'A++*', 'A++', 'A+*', 'A+', 'A*',
  'A', 'B+', 'B', 'C+', 'C', 'D+', 'D', 'E', 'N/A'];

var VISITS_TAB = 'Visits';
var RANKING_TAB = 'Ranking';
var BREAKDOWN_TAB = 'Breakdown';

function syncAll() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var rankSheet = getOrCreateSheet(ss, RANKING_TAB); // grab early: status cell must be reachable even on failure
  try {
    var cfg = getConfig();
    var officersById = fetchOfficersMap(cfg);

    var visits = fetchAllPages(cfg, '/rest/v1/visits',
      'id,officer_id,institute,association,district,purpose,ref_no,ref_date,start_date,end_date,category,remarks,created_at',
      { deleted: 'eq.false', order: 'created_at.asc' });
    writeVisitsTab(ss, visits, officersById);

    var rankRows = fetchAllPages(cfg, '/rest/v1/rank_summary',
      'name,total_visits,total_points,cat_counts',
      { order: 'total_points.desc' });
    writeRankingTab(rankSheet, rankRows);
    writeBreakdownTab(ss, rankRows);

    setStatus(rankSheet, 'OK — ' + new Date().toISOString());
  } catch (err) {
    setStatus(rankSheet, 'ERROR — ' + err.message);
    Logger.log(err);
  }
}

// ---------- config + fetch ----------

function getConfig() {
  var props = PropertiesService.getScriptProperties();
  var url = props.getProperty('SUPABASE_URL');
  var key = props.getProperty('SERVICE_KEY');
  if (!url || !key) {
    throw new Error('Missing SUPABASE_URL / SERVICE_KEY in Project Settings > Script Properties.');
  }
  return { url: url.replace(/\/$/, ''), key: key };
}

function authHeaders(cfg) {
  return { apikey: cfg.key, Authorization: 'Bearer ' + cfg.key, Accept: 'application/json' };
}

function buildUrl(base, path, params) {
  var qs = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
  }).join('&');
  return base + path + (qs ? '?' + qs : '');
}

function fetchJson(url, headers) {
  var res = UrlFetchApp.fetch(url, { headers: headers, muteHttpExceptions: true });
  var code = res.getResponseCode();
  if (code < 200 || code >= 300) {
    throw new Error('PostgREST ' + code + ' on ' + url + ': ' + res.getContentText().slice(0, 300));
  }
  return JSON.parse(res.getContentText());
}

// PostgREST page size cap is 1000 by default; loop limit/offset until a short page comes back.
// Full-refresh (not incremental upsert) keeps this + the *_TAB writers simple and robust at
// our ~2-3k row scale — re-derive everything every run instead of tracking deltas.
function fetchAllPages(cfg, path, selectClause, filterParams) {
  var headers = authHeaders(cfg);
  var limit = 1000, offset = 0, all = [];
  while (true) {
    var params = Object.assign({ select: selectClause, limit: limit, offset: offset }, filterParams || {});
    var page = fetchJson(buildUrl(cfg.url, path, params), headers);
    all = all.concat(page);
    if (page.length < limit) break;
    offset += limit;
  }
  return all;
}

function fetchOfficersMap(cfg) {
  var rows = fetchAllPages(cfg, '/rest/v1/officers', 'id,name', {});
  var map = {};
  rows.forEach(function (o) { map[o.id] = o.name; });
  return map;
}

// ---------- sheet writers ----------

function getOrCreateSheet(ss, name) {
  var sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  return sh;
}

function writeVisitsTab(ss, visits, officersById) {
  var sh = getOrCreateSheet(ss, VISITS_TAB);
  sh.clear();
  var header = ['Timestamp', 'Officer', 'Institute & Address', 'Association', 'District',
    'Purpose', 'Ref No', 'Ref Date', 'Start Date', 'End Date', 'Category', 'Remarks', 'id'];
  var rows = visits.map(function (v) {
    return [
      v.created_at || '',
      officersById[v.officer_id] || v.officer_id || '',
      v.institute || '',
      v.association || '',
      v.district || '',
      v.purpose || '',
      v.ref_no || '-',
      v.ref_date || '',
      v.start_date || '',
      v.end_date || '',
      v.category || '',
      v.remarks || '',
      v.id
    ];
  });
  var all = [header].concat(rows);
  sh.getRange(1, 1, all.length, header.length).setValues(all);
  sh.getRange(1, 1, 1, header.length).setFontWeight('bold');
  sh.setColumnWidth(header.length, 30); // squeeze id column narrow — no manual "hide column" step needed
}

function writeRankingTab(sh, rankRows) {
  sh.clear();
  sh.getRange(1, 1).setValue('SICIP QA Visit — Ranking').setFontWeight('bold').setFontSize(14);
  sh.getRange(2, 1).setValue('Updated: ' + new Date().toISOString());

  var header = ['Position', 'Officer', 'Total Visits', 'Total Points'];
  var startRow = 4;
  var rows = rankRows.map(function (r, i) { return [i + 1, r.name, r.total_visits, r.total_points]; });
  var all = [header].concat(rows);
  sh.getRange(startRow, 1, all.length, header.length).setValues(all);
  sh.getRange(startRow, 1, 1, header.length).setFontWeight('bold');
  sh.setColumnWidths(1, 4, 120); // keeps the table compact -> fits one screen
}

function writeBreakdownTab(ss, rankRows) {
  var sh = getOrCreateSheet(ss, BREAKDOWN_TAB);
  sh.clear();
  var header = ['Officer'].concat(CATEGORY_CODES);
  var rows = rankRows.map(function (r) {
    var cc = r.cat_counts || {};
    return [r.name].concat(CATEGORY_CODES.map(function (c) { return cc[c] || 0; }));
  });
  var all = [header].concat(rows);
  sh.getRange(1, 1, all.length, header.length).setValues(all);
  sh.getRange(1, 1, 1, header.length).setFontWeight('bold');
}

// Status corner cell, column F — deliberately outside the A:D ranking table so
// the table itself stays exactly 4 columns.
function setStatus(rankSheet, text) {
  rankSheet.getRange(1, 6).setValue('Status: ' + text);
}
