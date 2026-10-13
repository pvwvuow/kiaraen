-- 00006: cloud sync of the web app's learning state (kiara.v1 blob).
--
-- The WebView app (v0.6.0) keeps its whole learning state as one JSON blob.
-- Auth itself needs no table (GoTrue), but syncing that blob needs one row
-- per user. Last-writer-wins on the whole blob: the client compares its own
-- client_updated_at against the server's before adopting/pushing.
--
-- ⚠️ Run this in the Supabase SQL editor if the project was bootstrapped
-- before this file existed (idempotent — safe to re-run).

create table if not exists public.app_state (
    user_id           uuid primary key references auth.users(id) on delete cascade,
    data              jsonb not null default '{}'::jsonb,
    client_updated_at timestamptz not null default now(),  -- device clock of the last change (LWW key)
    server_updated_at timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

alter table public.app_state enable row level security;

drop policy if exists "app_state_select_own" on public.app_state;
create policy "app_state_select_own" on public.app_state
    for select to authenticated
    using (auth.uid() = user_id);

drop policy if exists "app_state_insert_own" on public.app_state;
create policy "app_state_insert_own" on public.app_state
    for insert to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "app_state_update_own" on public.app_state;
create policy "app_state_update_own" on public.app_state
    for update to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "app_state_delete_own" on public.app_state;
create policy "app_state_delete_own" on public.app_state
    for delete to authenticated
    using (auth.uid() = user_id);
