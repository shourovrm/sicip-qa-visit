-- leave lifecycle: scheduled -> started -> completed (or scheduled -> cancelled).
-- legacy 'availed' folds into 'completed' and drops out of the allowed set.
update public.leaves set status = 'completed' where status = 'availed';

alter table public.leaves drop constraint leaves_status_check;
alter table public.leaves add constraint leaves_status_check
  check (status in ('scheduled', 'started', 'completed', 'cancelled'));
