-- Remove is_pinned column from public.workflows table as it is now local to the device
alter table public.workflows drop column if exists is_pinned;
