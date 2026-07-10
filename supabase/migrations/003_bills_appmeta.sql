-- immutable submitted-bill archive + app update-notice metadata

-- one row per submitted bill: frozen snapshot (trips, travel rows, nights/food, totals) in data
create table public.bills (
  id         uuid primary key default gen_random_uuid(),
  officer_id uuid not null references public.officers (id),
  bill_date  date not null,
  data       jsonb not null,
  net        numeric(12,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted    boolean not null default false
);
create trigger set_updated_at before update on public.bills
  for each row execute function extensions.moddatetime(updated_at);
create index on public.bills (updated_at);

alter table public.bills enable row level security;
-- bills are private: own rows only (+ admin)
create policy bills_select on public.bills for select to authenticated
  using (officer_id = auth.uid() or is_admin());
create policy bills_write on public.bills for all to authenticated
  using (officer_id = auth.uid() or is_admin())
  with check (officer_id = auth.uid() or is_admin());

-- key/value app metadata: latest_version + apk_url drive the in-app update banner
create table public.app_meta (
  key        text primary key,
  value      text not null,
  updated_at timestamptz not null default now()
);
create trigger set_updated_at before update on public.app_meta
  for each row execute function extensions.moddatetime(updated_at);

alter table public.app_meta enable row level security;
create policy app_meta_select on public.app_meta for select to authenticated using (true);
create policy app_meta_admin  on public.app_meta for all to authenticated
  using (is_admin()) with check (is_admin());

insert into public.app_meta (key, value) values
  ('latest_version', '1.2.0'),
  ('apk_url', '');
