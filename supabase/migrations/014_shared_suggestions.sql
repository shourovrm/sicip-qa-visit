-- Shared suggestion lists (only 'equipment' today): every officer reads them as typing
-- hints; writes go only through add_suggestions(). value_key folds case + outer spaces so
-- "Grinder " and "grinder" are one entry; the latest spelling wins, uses counts additions.
create table public.shared_suggestions (
  list       text not null,
  value      text not null check (char_length(value) between 1 and 120),
  value_key  text not null,
  uses       int not null default 1,
  updated_at timestamptz not null default now(),
  primary key (list, value_key)
);

alter table public.shared_suggestions enable row level security;
create policy shared_suggestions_select on public.shared_suggestions for select to authenticated
  using (true);

create or replace function public.add_suggestions(p_list text, p_values text[])
returns void language plpgsql security definer set search_path = public as
$$
begin
  if p_list is distinct from 'equipment' then
    raise exception 'unknown suggestion list %', p_list;
  end if;
  -- one row per key per call, else ON CONFLICT would touch the same row twice
  insert into shared_suggestions (list, value, value_key)
  select distinct on (lower(trim(v))) p_list, trim(v), lower(trim(v))
  from unnest(p_values) as v
  where trim(coalesce(v, '')) <> '' and char_length(trim(v)) <= 120
  on conflict (list, value_key) do update
    set value = excluded.value, uses = shared_suggestions.uses + 1, updated_at = now();
end
$$;

revoke all on function public.add_suggestions(text, text[]) from public, anon;
grant execute on function public.add_suggestions(text, text[]) to authenticated;

-- backfill from every live report's equipment cards (uses = number of reports naming it)
insert into public.shared_suggestions (list, value, value_key, uses)
select 'equipment', max(name), key, count(distinct report_id)
from (
  select r.id as report_id, trim(card ->> 'name') as name, lower(trim(card ->> 'name')) as key
  from public.reports r, jsonb_array_elements(
    case when jsonb_typeof(r.data -> 'cards' -> 'equipment') = 'array'
      then r.data -> 'cards' -> 'equipment' else '[]'::jsonb end) as card
  where not r.deleted
) names
where name <> '' and char_length(name) <= 120
group by key
on conflict (list, value_key) do nothing;
