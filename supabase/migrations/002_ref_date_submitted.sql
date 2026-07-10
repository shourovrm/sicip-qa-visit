-- ref order date (printed in bill purpose line) + bill-submitted flag on tours
alter table public.visits add column if not exists ref_date date;
alter table public.trips add column if not exists submitted boolean not null default false;
