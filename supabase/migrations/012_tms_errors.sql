-- TMS API-change alerts. Phones insert one row per (endpoint, kind, app_version, day) with
-- PostgREST ignore-duplicates (on conflict do nothing -- needs INSERT policy only, no select).
-- detail is short text only, never response bodies. Admin reads + marks resolved.
create table public.tms_errors (
  id          uuid primary key default gen_random_uuid(),
  created_at  timestamptz not null default now(),
  officer_id  uuid default auth.uid() references public.officers (id),
  app_version text not null,
  endpoint    text not null,
  kind        text not null check (kind in ('http_404', 'http_405', 'not_json', 'shape')),
  detail      text not null check (char_length(detail) <= 300),
  day         date not null default current_date,
  resolved_at timestamptz,
  unique (endpoint, kind, app_version, day)
);

alter table public.tms_errors enable row level security;
create policy tms_errors_insert on public.tms_errors for insert to authenticated
  with check (officer_id = auth.uid());
create policy tms_errors_select on public.tms_errors for select to authenticated
  using (is_admin());
create policy tms_errors_update on public.tms_errors for update to authenticated
  using (is_admin()) with check (is_admin());
