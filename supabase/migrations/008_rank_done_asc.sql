-- rank_summary v2: only done visits score (scheduled = 0 until End tour), and rank is
-- ascending -- fewest points = #1 (house convention). Same view shape, feeds Sheets bridge.
create or replace view public.rank_summary as
with counts as (
  select
    v.officer_id,
    v.category,
    count(*)::int as cnt,
    (count(*) * case v.category
      when 'A***' then 116
      when 'A**+' then 112
      when 'A**'  then 100
      when 'A++*' then 96
      when 'A++'  then 84
      when 'A+*'  then 80
      when 'A+'   then 68
      when 'A*'   then 64
      when 'A'    then 52
      when 'B+'   then 48
      when 'B'    then 36
      when 'C+'   then 32
      when 'C'    then 20
      when 'D+'   then 16
      when 'D'    then 4
      when 'E'    then 1
      else 0 -- N/A
    end)::int as pts
  from public.visits v
  where v.deleted = false and v.status = 'done' -- scheduled visits never score
  group by v.officer_id, v.category
)
select
  o.name,
  coalesce(sum(c.cnt), 0)::int as total_visits,
  coalesce(sum(c.pts), 0)::int as total_points,
  coalesce(jsonb_object_agg(c.category, c.cnt) filter (where c.category is not null), '{}'::jsonb) as cat_counts
from public.officers o
left join counts c on c.officer_id = o.id
where o.active = true
group by o.id, o.name
order by total_points asc; -- fewest points = rank 1
