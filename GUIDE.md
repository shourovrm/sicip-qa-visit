# SICIP QA Visit — Startup Guide

## Install
1. APK: `SICIP-QA-Visit-v1.2.0.apk` (repo root, also `android/app/build/outputs/apk/release/`).
2. Copy to phone (USB / Drive / WhatsApp-to-self), tap it, allow "install unknown apps" when prompted. No Play Store, no Google services needed. Android 8+.

## First login (needs internet once)
- Email: your account email.
- Password: temp passwords live on the dev machine in `tools/passwords.txt` (never in git). Find yours:
  ```
  grep <your-email> tools/passwords.txt
  ```
  Admin (Riad): `grep shourovrm tools/passwords.txt`.
- After login the app works fully offline; data syncs automatically every ~15 min when online, or tap the sync chip.
- Change password anytime: Profile → Change password (needs internet).
- Other 8 officers: accounts exist with placeholder emails — before rollout, admin activates each (real email + fresh temp password) and hands the password over in person/WhatsApp.

## Daily use
- **Schedule a visit**: Home → orange + button. District Dhaka asks inside/outside metro (affects points: E=1 / D=4).
- **Tour**: tap **Start** on an upcoming visit card → confirm start time (add more visits or inform a colleague if you like) → during the tour add activities or ad-hoc visits → **End tour** (end date+time; category auto-suggested, override allowed). Travel and fares are NOT entered during the tour.
- **Travel & fares**: added later in Visits → TA/DA Bill → select tour(s) → "Add travel" rows (any movement: bus stand→institute, hotel→ghat; mode, class, fare) → totals update live.
- **Multiple visits in one tour**: first visit scores, others auto N/A — matches the office scoring sheet.
- **Team**: who's on visit / on leave / in office (derived — no check-in needed) + points leaderboard.
- **Leaves**: Leaves tab → Add leave (Casual / Sick / Emergency / Others).
- **TA/DA bill**: Visits → TA/DA Bill → New bill tab → tick finished tour(s) → add/edit travel rows, adjust nights/food (inside-metro tours default to 0/0) → **Generate PDF** for a draft, or **Submit bill** to finalize — submitting freezes the values, files it under **Previous bills** (read-only, view PDF anytime), and cannot be edited after.
- **Ref numbers**: while editing a visit, typing in the ref box suggests refs colleagues already used; picking one auto-fills its date.
- **Theme**: Profile → System / Light / Dark.
- **Visits tabs**: Scheduled (upcoming + on-tour) and Completed (finished, with category). Category is set when you finish a tour — not before.
- **Filters**: chips on both tabs — period (incl. custom range), district, category, purpose. Rank has Overall | Last month.
- **Updates**: when a new APK is released, a banner appears on Home with a Get button — no store needed.
- **Visit Scores sheet**: Profile → opens the Google Sheet (view-only) in browser.

## Admin notes (Riad)
- Keystore `android/keystore/` (gitignored) — BACK IT UP; same key must sign every future update (valid ~27 years, signing is automatic on build).
- Rebuild APK: `cd android && ./g :app:assembleRelease`.
- Rotate temp passwords before rollout: re-run activation per officer (fresh script when web app milestone lands, or ask the assistant).
- DB: Supabase project `twpehiqfdjfprtwsiwqp` (Singapore). Web admin app + Sheets auto-export = upcoming milestones.

## Troubleshooting
- "No connection — try again" at login: needs internet for the FIRST login only.
- Sync chip shows ⚠: tap it to retry; check network. Offline edits are kept and pushed later.
- Forgot password: ask admin to set a new one (email reset arrives with the web app).
