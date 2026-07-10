# SICIP QA Visit — Startup Guide

## Install
1. APK: `SICIP-QA-Visit-v1.0.0.apk` (repo root, also `android/app/build/outputs/apk/release/`).
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
- **Trip**: Home → Start trip → attach scheduled visit(s) → optionally inform a colleague → add travel legs (mode, class, fare) as you go → add activities/ad-hoc visits during → Finish trip. Category + points auto-computed from dates (override offered at finish).
- **Multiple visits in one trip**: first visit scores, others auto N/A — matches the office scoring sheet.
- **Team**: who's on visit / on leave / in office (derived — no check-in needed) + points leaderboard.
- **Leaves**: Leaves tab → Add leave (Casual / Sick / Emergency / Others).
- **TA/DA bill**: Visits → TA/DA Bill → tick the finished trip(s) for the claim → adjust nights/food per trip if needed (e.g. zero out a same-day Dhaka trip) → Generate → PDF saved to Downloads + share sheet. Layout matches the official template exactly.
- **Theme**: Profile → System / Light / Dark.
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
