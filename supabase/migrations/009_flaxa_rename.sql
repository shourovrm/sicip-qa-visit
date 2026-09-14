-- LFMEAB association renamed FLAXA (org rebrand); picklist seeds updated in both apps.
update public.visits set association = 'FLAXA' where association = 'LFMEAB';
