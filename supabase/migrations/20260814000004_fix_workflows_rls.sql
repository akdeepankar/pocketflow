-- 1. Enable RLS on workflows table
alter table public.workflows enable row level security;

-- Drop all open or legacy policies
drop policy if exists "Allow all access to workflows" on public.workflows;
drop policy if exists "Allow read access to workflows" on public.workflows;
drop policy if exists "Allow insert access to workflows" on public.workflows;
drop policy if exists "Allow update access to workflows" on public.workflows;
drop policy if exists "Allow delete access to workflows" on public.workflows;
drop policy if exists "Users can only view their own or joined workflows" on public.workflows;
drop policy if exists "Users can insert their own workflows" on public.workflows;
drop policy if exists "Users can update their own or joined workflows" on public.workflows;
drop policy if exists "Only owners can delete workflows" on public.workflows;

-- 2. Strict RLS Policies: Workflows are PRIVATE by default.
-- Users can ONLY query workflows they created, own, or are listed as a member in.
create policy "Users can only view their own or joined workflows"
  on public.workflows for select
  using (
    auth.uid()::text = user_id
    or auth.uid()::text = owner_user_id
    or members_json like '%' || auth.uid()::text || '%'
  );

-- Users can only insert workflows owned by themselves
create policy "Users can insert their own workflows"
  on public.workflows for insert
  with check (
    auth.uid()::text = user_id
    or auth.uid()::text = owner_user_id
  );

-- Users can only update workflows they own or are members of
create policy "Users can update their own or joined workflows"
  on public.workflows for update
  using (
    auth.uid()::text = user_id
    or auth.uid()::text = owner_user_id
    or members_json like '%' || auth.uid()::text || '%'
  );

-- Only owner can delete their workflow
create policy "Only owners can delete workflows"
  on public.workflows for delete
  using (
    auth.uid()::text = user_id
    or auth.uid()::text = owner_user_id
  );

-- 3. Secure Join-by-Code RPC Function
-- Only someone who enters the exact 5-character join code can access and join that specific workflow.
create or replace function public.join_workflow_by_code(
    p_code text,
    p_user_id text,
    p_user_name text,
    p_user_email text default ''
)
returns json
language plpgsql
security definer
as $$
declare
    v_workflow public.workflows%rowtype;
    v_members jsonb;
    v_member_count int;
    v_new_member jsonb;
begin
    -- 1. Find workflow by exact 5-character join code
    select * into v_workflow
    from public.workflows
    where upper(trim(join_code)) = upper(trim(p_code))
    limit 1;

    if not found then
        return json_build_object('status', 'not_found');
    end if;

    -- 2. Parse members JSON array
    v_members := coalesce(v_workflow.members_json::jsonb, '[]'::jsonb);
    v_member_count := jsonb_array_length(v_members);

    -- 3. Check if caller is already owner or member
    if v_workflow.owner_user_id = p_user_id or v_workflow.user_id = p_user_id then
        return json_build_object('status', 'already_member', 'workflow', row_to_json(v_workflow));
    end if;

    if exists (select 1 from jsonb_array_elements(v_members) as m where m->>'userId' = p_user_id) then
        return json_build_object('status', 'already_member', 'workflow', row_to_json(v_workflow));
    end if;

    -- 4. Check seat limit (max 3 collaborators)
    if v_member_count >= 3 then
        return json_build_object('status', 'seats_full');
    end if;

    -- 5. Add user to members list securely
    v_new_member := json_build_object(
        'userId', p_user_id,
        'userName', p_user_name,
        'userEmail', p_user_email,
        'joinedAt', (extract(epoch from now()) * 1000)::bigint
    );
    v_members := v_members || v_new_member;

    update public.workflows
    set members_json = v_members::text
    where id = v_workflow.id
    returning * into v_workflow;

    return json_build_object('status', 'success', 'workflow', row_to_json(v_workflow));
end;
$$;

-- Grant execution permissions for the join function
grant execute on function public.join_workflow_by_code(text, text, text, text) to anon, authenticated, service_role;
