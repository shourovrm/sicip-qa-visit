-- visit reports (surprise visit now, monitoring/QA later). answers live in data jsonb,
-- shaped by shared/report-templates/<type>-v<template_version>.json.
create table public.reports (
  id               uuid primary key default gen_random_uuid(),
  officer_id       uuid not null references public.officers (id),
  visit_id         uuid not null references public.visits (id),
  type             text not null check (type in ('surprise', 'monitoring')),
  template_version int  not null,
  data             jsonb not null default '{}'::jsonb,
  status           text not null default 'draft' check (status in ('draft', 'submitted')),
  submitted_at     timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted          boolean not null default false
);
create trigger set_updated_at before update on public.reports
  for each row execute function extensions.moddatetime(updated_at);
create index on public.reports (updated_at);
create index on public.reports (visit_id);

alter table public.reports enable row level security;
-- reports are private like bills: own rows + admin. submitted = read-only is enforced
-- client-side (an RLS status gate would wedge sync on a re-pushed submitted row).
create policy reports_select on public.reports for select to authenticated
  using (officer_id = auth.uid() or is_admin());
create policy reports_write on public.reports for all to authenticated
  using (officer_id = auth.uid() or is_admin())
  with check (officer_id = auth.uid() or is_admin());
