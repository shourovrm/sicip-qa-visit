-- extend category scale past the old 100 cap: A**+ (7D7N=112), A*** (8D7N=116)
alter table public.visits drop constraint visits_category_check;
alter table public.visits add constraint visits_category_check
  check (category in ('A***','A**+','A**','A++*','A++','A+*','A+','A*','A','B+','B','C+','C','D+','D','E','N/A'));
