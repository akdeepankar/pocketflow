-- Drop existing workflows table if exists for clean recreation
drop table if exists public.workflows;

-- Create workflows table
create table public.workflows (
    id text primary key,
    user_id text,
    name text,
    description text,
    last_edited bigint,
    created_at_date text,
    is_pinned boolean,
    card_color_hex text,
    nodes_json text,
    edges_json text,
    join_code text,
    owner_user_id text,
    members_json text,
    canvas_transform_json text,
    activity_log_json text,
    is_cloud boolean
);

-- Create presence table
create table if not exists public.presence (
    id text primary key,
    workflow_id text,
    user_id text,
    user_name text,
    updated_at bigint,
    action text,
    cursor_x real,
    cursor_y real,
    node_id text
);

-- Enable Realtime publication for both tables
alter publication supabase_realtime add table public.workflows;
alter publication supabase_realtime add table public.presence;

-- Programmatically create the public 'node-images' storage bucket
insert into storage.buckets (id, name, public)
values ('node-images', 'node-images', true)
on conflict (id) do nothing;

-- Set storage policies for public access to 'node-images'
create policy "Allow public read access to node-images"
  on storage.objects for select
  using ( bucket_id = 'node-images' );

create policy "Allow public insert access to node-images"
  on storage.objects for insert
  with check ( bucket_id = 'node-images' );

create policy "Allow public delete access to node-images"
  on storage.objects for delete
  using ( bucket_id = 'node-images' );
