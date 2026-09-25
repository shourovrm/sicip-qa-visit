-- Monitoring Visit split into Surprise/QA (see docs/superpowers/plans/2026-09-25-qa-report.md
-- section 1). visit_type decides which report template a visit's report uses (null for every
-- other purpose). "Surprise Visit" purpose is retired -- its rows move to
-- purpose='Monitoring Visit', visit_type='surprise' so they keep their existing surprise report.
alter table public.visits
  add column visit_type text check (visit_type in ('surprise', 'qa'));

update public.visits
  set purpose = 'Monitoring Visit', visit_type = 'surprise'
  where purpose = 'Surprise Visit';
