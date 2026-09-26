-- Re-create supabase_realtime publication if it does not exist
do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
exception
  when others then
    raise notice 'Publication supabase_realtime already exists or could not be created';
end $$;

-- Enable replication for workflows table to support database changes safely
do $$
begin
  alter publication supabase_realtime add table public.workflows;
exception
  when duplicate_object then
    raise notice 'relation "workflows" is already member of publication "supabase_realtime"';
end $$;
