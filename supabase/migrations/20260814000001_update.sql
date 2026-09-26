-- Drop and recreate public.workflows table to match all fields in SupabaseWorkflow
drop table if exists public.workflows cascade;

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

-- Re-enable Realtime publication for workflows
alter publication supabase_realtime add table public.workflows;
