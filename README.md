# SICIP QA Visit

Visit management for field officers: schedule visits, run tours, log travel, auto-score
performance, and generate official TA/DA bills. Built for the Program Officers (QA) of
SICIP (Finance Division, Ministry of Finance, Bangladesh) — 9 users, zero hosting cost.

**[⬇ Download the Android app (release APK)](https://github.com/shourovrm/sicip-qa-visit/releases/latest)** — Android 8+, ~1.7 MB, no Google services required.

## What's inside

| Part | Stack | Role |
|---|---|---|
| `android/` | Kotlin, Jetpack Compose, Room, WorkManager | Main app for field officers — fully offline-first |
| `web/` | Svelte + Vite + supabase-js (static SPA) | Desk work: edit data, xlsx/PDF bills, admin panel, password reset |
| `supabase/` | Postgres + Auth + RLS (free tier) | Single source of truth; SQL migrations included |
| `tools/` | Python + Apps Script | Account admin, data import, Google Sheets mirror |

## Architecture notes

**Android** — release-only builds, R8-minified, **no GMS/Firebase/native libs**: the APK is
ABI-universal, installs by sideload, and every feature (including notifications and visit
reminders) works without Google services. Room is the local source of truth; a WorkManager
sync engine pushes dirty rows and pulls by `updated_at` watermark (last-write-wins, soft
deletes). Officers work offline for days; sync happens whenever network appears. TA/DA
bills render on-device as PDF via an HTML template printed through WebView.

**Web** — no server at all: a static SPA calling Supabase directly (the publishable key is
safe in the browser; Postgres row-level security does the enforcement). Bills are generated
client-side: real `.xlsx` by filling the official template with exceljs, PDF via the same
HTML layout the Android app uses. Deployed as a Cloudflare Worker with static assets
(`wrangler deploy`), free tier.

**Supabase** — auth (email/password), 7 tables, RLS policies: everyone signed-in reads all
(rank/team pages), writes own rows only, `role='admin'` bypasses. Scoring is category-based
(see `CATEGORIES.md`): each visit category encodes a day/night span that yields both rank
points and bill allowances.

**Brevo** — free SMTP relay behind Supabase auth emails (password reset). ~300 mails/day
free, far beyond a small team's needs.

**Google Sheets** — optional one-way mirror: an Apps Script (`tools/sheets/bridge.gs`) pulls
from Supabase hourly and rewrites Ranking / Visits / Breakdown / Categories tabs, so office
staff who live in Sheets see live data without touching the DB.

## Replicating the whole system

1. **Supabase**: create a free project (pick a nearby region) → SQL editor → run
   `supabase/migrations/*.sql` in order → put your project URL + publishable key in
   `supabase/config.json` and `web/src/lib/supabase.js`, and in
   `android/.../data/remote/SupabaseConfig.kt`.
2. **Accounts**: create auth users (dashboard or `tools/create_officers.py` — needs a
   personal access token in `supabase/.token`). A DB trigger auto-creates officer rows.
3. **Bill template**: put your own `.xlsx` at `assets/tada-template.xlsx`.
   `assets/tada-template.example.xlsx` shows the expected layout with sample data.
4. **Android build**: needs JDK 21 + Android SDK (platforms 33+) + Gradle 8.13
   (`android/g` is a thin launcher that finds a cached Gradle dist; use your own Gradle or
   the wrapper if you prefer). First build: generate a keystore under `android/keystore/`
   (see `android/app/build.gradle.kts` for the expected `keystore.properties` keys) — then
   `cd android && ./g :app:assembleRelease`. **Back the keystore up**: updates must be
   signed with the same key.
5. **Web deploy**: `cd web && bun install && bun run build` (copies the bill template into
   `dist/`), then Cloudflare: create a free account, `wrangler deploy` with an API token
   (edit `web/wrangler.toml` account id), or drag `dist/` into the dashboard (Workers &
   Pages → Upload assets).
6. **Password reset emails**: Supabase → Auth → URL config (site URL + `https://<site>/reset`
   redirect) + custom SMTP (Brevo: host `smtp-relay.brevo.com`, port 587, the generated
   `@smtp-brevo.com` login — not your account email — and an SMTP key).
7. **Sheets mirror** (optional): follow `tools/sheets/SETUP.md`.

## Development

- `GUIDE.md` — end-user manual. `CATEGORIES.md` — scoring/allowance table.
- Android tests: `cd android && ./g :app:testReleaseUnitTest` (180+ unit tests: scoring,
  bill math, sync merge logic). Web tests: `cd web && bun test`.
- Style: small single-purpose files, offline-first, category drives everything
  (points and allowances derive from one code — no duplicated business rules).
