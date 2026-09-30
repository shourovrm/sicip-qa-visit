-- yes/no for every officer: any unresolved TMS API-change row? (table itself is admin-only)
create or replace function public.tms_api_broken()
returns boolean language sql stable security definer set search_path = public as
$$ select exists (select 1 from tms_errors where resolved_at is null) $$;

revoke all on function public.tms_api_broken() from public, anon;
grant execute on function public.tms_api_broken() to authenticated;
