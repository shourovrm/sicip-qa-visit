# Deploying the SICIP QA Visit web app

Static SPA — no server, no env vars (Supabase URL + publishable anon key are baked in at build;
they are safe for browsers, RLS does the access control).

## 1. Build

```sh
cd web
bun install
bun run build        # copies assets/tada-template.xlsx into public/ then vite-builds to dist/
```

`dist/` is the whole deployable. The TA/DA template ships inside it (`/tada-template.xlsx`) —
the build fails if `assets/tada-template.xlsx` is missing locally (it's gitignored; keep a copy).

## 2. Cloudflare Pages

**Dashboard (simplest):**
1. Cloudflare dashboard → Workers & Pages → Create → Pages → *Upload assets*.
2. Project name e.g. `sicip-qa-visit` → upload the `dist/` folder → Deploy.
3. Note the URL: `https://sicip-qa-visit.pages.dev` (custom domain optional, in project → Custom domains).

**CLI (repeatable):**
```sh
bunx wrangler pages deploy dist --project-name sicip-qa-visit
```
(first run: `bunx wrangler login`)

**SPA routing note:** the app uses hash routing (`/#/visits`) so no redirect rules are needed.
The only non-root path is `/reset`; Pages serves `index.html` for unknown paths automatically
(single-page-app fallback), so it works out of the box.

## 3. Supabase — password reset redirect

Dashboard → Authentication → URL Configuration:
1. **Site URL**: `https://<your-site>.pages.dev`
2. **Redirect URLs**: add `https://<your-site>.pages.dev/reset`

Without this the recovery email's link is refused by Supabase.

## 4. Supabase — SMTP (so reset emails actually send)

Default Supabase mailer is rate-limited to a trickle and lands in spam. Use Brevo (free tier, 300 mails/day):

1. brevo.com → sign up → **SMTP & API** → SMTP tab → note server/port/login, generate an SMTP key.
2. Verify a sender address (Brevo → Senders) — e.g. the project Gmail.
3. Supabase dashboard → Project Settings → Authentication → **SMTP Settings** → Enable custom SMTP:
   - Host: `smtp-relay.brevo.com`, Port: `587`
   - Username: your Brevo login email
   - Password: the SMTP key
   - Sender email: the verified sender; Sender name: `SICIP QA Visit`
4. Save, then send a test reset from the app's login page ("Forgot password?").

## 5. Officer accounts

Created/activated via local scripts in `tools/` (service-role key required — never ship it to the
browser). Not part of the web app by design.
