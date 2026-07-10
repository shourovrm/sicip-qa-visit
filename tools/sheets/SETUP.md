# Sheets bridge setup

One-way mirror: Supabase DB -> the Google Sheet. DB always wins — every
`syncAll()` run wipes and rewrites the `Visits`, `Ranking`, `Breakdown` tabs
from scratch. Any other tab in the spreadsheet is left alone.

## Install

1. Open the target Google Sheet.
2. Extensions -> Apps Script.
3. Delete the boilerplate `Code.gs` content, paste in `tools/sheets/bridge.gs`. Save.
4. Project Settings (gear icon, left sidebar) -> Script Properties -> Add:
   - `SUPABASE_URL` = `https://twpehiqfdjfprtwsiwqp.supabase.co`
   - `SERVICE_KEY` = the **legacy** `service_role` key (long `eyJ...` JWT) —
     Supabase dashboard -> Settings -> API Keys -> "Legacy API keys" -> reveal
     `service_role`. Do NOT use a new-style `sb_secret_...` key: Supabase
     rejects those from Apps Script with "Forbidden use of secret API key in
     browser" (UrlFetchApp's Mozilla-style User-Agent trips the heuristic).
     **Keep this secret**: it bypasses RLS.
     Script Properties are private to the script owner, which is why it's safe
     to store here — never put it in a cell or in code.
5. Back in the editor, select `syncAll` from the function dropdown, click Run.
   Approve the OAuth prompts (this script only talks to Supabase and this
   spreadsheet).
6. Confirm three tabs now exist: `Visits`, `Ranking`, `Breakdown`. `Ranking`
   corner cell F1 should read `Status: OK — <timestamp>`.
7. Triggers (clock icon, left sidebar) -> Add Trigger -> function `syncAll`,
   event source "Time-driven", type "Hour timer", every hour. Save.

## Behavior

- One-way: the sheet never writes back to the DB. Edits to `Visits`,
  `Ranking`, or `Breakdown` are lost on the next sync.
- Full refresh, not incremental: each run re-pulls everything and calls
  `setValues` once per tab. Simpler and more robust than row-level upsert at
  this scale (a few thousand rows).
- Errors don't stop silently: check `Ranking!F1` for `Status: OK` vs
  `Status: ERROR — <message>` after each run, or Apps Script's Executions log.

## Pointing at a different spreadsheet

Bind-agnostic: the script only touches `SpreadsheetApp.getActiveSpreadsheet()`
and the Script Properties. To mirror into a new sheet, repeat the Install
steps in that sheet — no code changes.
