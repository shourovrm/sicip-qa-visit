-- SICIP QA Visit — initial schema. Paste whole file into Supabase SQL Editor and Run.

-- auto-bump updated_at on update
create extension if not exists moddatetime schema extensions;

-- ============ officers ============
-- one row per auth user. role gates admin powers.
create table public.officers (
  id         uuid primary key references auth.users (id) on delete cascade,
  name       text not null,
  email      text not null unique,
  role       text not null default 'officer' check (role in ('officer', 'admin')),
  active     boolean not null default true,
  updated_at timestamptz not null default now()
);

-- admin check used by every policy below
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as
$$ select exists (select 1 from officers where id = auth.uid() and role = 'admin') $$;

-- new auth user -> officers row (name passed in user metadata at creation)
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.officers (id, email, name)
  values (new.id, new.email, coalesce(new.raw_user_meta_data ->> 'name', new.email));
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============ trips ============
-- one journey = one TA/DA bill. owns travel legs.
create table public.trips (
  id                  uuid primary key default gen_random_uuid(),
  officer_id          uuid not null references public.officers (id),
  status              text not null default 'active' check (status in ('active', 'finished')),
  started_at          timestamptz not null default now(),
  finished_at         timestamptz,
  informed_officer_id uuid references public.officers (id),
  updated_at          timestamptz not null default now(),
  deleted             boolean not null default false
);

-- ============ visits ============
-- one institute engagement; mirrors old Google Form row. category -> points in app code.
create table public.visits (
  id                uuid primary key default gen_random_uuid(),
  officer_id        uuid not null references public.officers (id),
  trip_id           uuid references public.trips (id),
  institute         text not null,
  association       text not null,
  district          text not null,
  dhaka_metro       boolean,            -- only meaningful when district = Dhaka
  purpose           text not null,
  ref_no            text,
  start_date        date not null,
  end_date          date not null,
  category          text not null default 'N/A'
                    check (category in ('A**','A++','A+','A','B','C','D','E','N/A')),
  category_override boolean not null default false,  -- true when user overrode auto value
  is_additional     boolean not null default false,  -- non-primary visit in a trip
  status            text not null default 'scheduled' check (status in ('scheduled', 'done')),
  remarks           text,
  source            text not null default 'app' check (source in ('app', 'sheet')),  -- sheet = imported history
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted           boolean not null default false
);

-- ============ travel_legs ============
-- one row per bill itinerary line. night_stay / food_day editable in bill preview.
create table public.travel_legs (
  id         uuid primary key default gen_random_uuid(),
  trip_id    uuid not null references public.trips (id),
  dep_date   date not null,
  dep_time   time not null,
  dep_place  text not null,
  arr_date   date not null,
  arr_time   time not null,
  arr_place  text not null,
  mode       text not null,
  class      text,
  fare       numeric(10,2) not null default 0,
  night_stay integer not null default 0,
  food_day   numeric(2,1) not null default 0,
  remarks    text,
  updated_at timestamptz not null default now(),
  deleted    boolean not null default false
);

-- ============ activities ============
-- timestamped note during a trip or visit
create table public.activities (
  id         uuid primary key default gen_random_uuid(),
  trip_id    uuid references public.trips (id),
  visit_id   uuid references public.visits (id),
  at         timestamptz not null default now(),
  note       text not null,
  updated_at timestamptz not null default now(),
  deleted    boolean not null default false,
  check (trip_id is not null or visit_id is not null)
);

-- ============ leaves ============
create table public.leaves (
  id                  uuid primary key default gen_random_uuid(),
  officer_id          uuid not null references public.officers (id),
  type                text not null check (type in ('Casual', 'Sick', 'Emergency', 'Others')),
  reason              text,
  informed_officer_id uuid references public.officers (id),
  start_date          date not null,
  end_date            date not null,
  status              text not null default 'scheduled'
                      check (status in ('scheduled', 'availed', 'cancelled')),
  updated_at          timestamptz not null default now(),
  deleted             boolean not null default false
);

-- ============ updated_at triggers ============
create trigger set_updated_at before update on public.officers    for each row execute function extensions.moddatetime(updated_at);
create trigger set_updated_at before update on public.trips       for each row execute function extensions.moddatetime(updated_at);
create trigger set_updated_at before update on public.visits      for each row execute function extensions.moddatetime(updated_at);
create trigger set_updated_at before update on public.travel_legs for each row execute function extensions.moddatetime(updated_at);
create trigger set_updated_at before update on public.activities  for each row execute function extensions.moddatetime(updated_at);
create trigger set_updated_at before update on public.leaves      for each row execute function extensions.moddatetime(updated_at);

-- ============ sync-pull indexes ============
create index on public.trips (updated_at);
create index on public.visits (updated_at);
create index on public.travel_legs (updated_at);
create index on public.activities (updated_at);
create index on public.leaves (updated_at);
create index on public.visits (officer_id);

-- ============ RLS ============
-- everyone signed-in reads everything (rank + team pages need it);
-- writes only on own rows; admin writes anywhere.
alter table public.officers    enable row level security;
alter table public.trips       enable row level security;
alter table public.visits      enable row level security;
alter table public.travel_legs enable row level security;
alter table public.activities  enable row level security;
alter table public.leaves      enable row level security;

-- officers: read all; only admin writes
create policy officers_select on public.officers for select to authenticated using (true);
create policy officers_admin  on public.officers for all    to authenticated using (is_admin()) with check (is_admin());

-- trips / visits / leaves: own rows or admin
create policy trips_select on public.trips for select to authenticated using (true);
create policy trips_write  on public.trips for all    to authenticated
  using (officer_id = auth.uid() or is_admin()) with check (officer_id = auth.uid() or is_admin());

create policy visits_select on public.visits for select to authenticated using (true);
create policy visits_write  on public.visits for all    to authenticated
  using (officer_id = auth.uid() or is_admin()) with check (officer_id = auth.uid() or is_admin());

create policy leaves_select on public.leaves for select to authenticated using (true);
create policy leaves_write  on public.leaves for all    to authenticated
  using (officer_id = auth.uid() or is_admin()) with check (officer_id = auth.uid() or is_admin());

-- travel_legs: ownership via parent trip
create policy legs_select on public.travel_legs for select to authenticated using (true);
create policy legs_write  on public.travel_legs for all to authenticated
  using (exists (select 1 from trips t where t.id = trip_id and (t.officer_id = auth.uid() or is_admin())))
  with check (exists (select 1 from trips t where t.id = trip_id and (t.officer_id = auth.uid() or is_admin())));

-- activities: ownership via parent trip or visit
create policy acts_select on public.activities for select to authenticated using (true);
create policy acts_write  on public.activities for all to authenticated
  using (
    exists (select 1 from trips  t where t.id = trip_id  and (t.officer_id = auth.uid() or is_admin()))
    or exists (select 1 from visits v where v.id = visit_id and (v.officer_id = auth.uid() or is_admin()))
  )
  with check (
    exists (select 1 from trips  t where t.id = trip_id  and (t.officer_id = auth.uid() or is_admin()))
    or exists (select 1 from visits v where v.id = visit_id and (v.officer_id = auth.uid() or is_admin()))
  );
