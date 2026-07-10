-- component scoring formula (day=4, night=12): allow the in-between night-heavy categories
alter table public.visits drop constraint visits_category_check;
alter table public.visits add constraint visits_category_check
  check (category in ('A**','A++*','A++','A+*','A+','A*','A','B+','B','C+','C','D+','D','E','N/A'));
