-- ═══════════════════════════════════════════════════════════════════════════
-- 00003_rls_policies.sql
-- Row Level Security for ALL user-owned tables.
-- Rule: a user can only ever see/modify rows where user_id = auth.uid().
-- Secrets: only the anon (publishable) key ships in the app; RLS is the fence.
-- ═══════════════════════════════════════════════════════════════════════════

alter table public.profiles           enable row level security;
alter table public.user_word_progress enable row level security;
alter table public.review_log         enable row level security;
alter table public.lesson_progress    enable row level security;
alter table public.quiz_attempts      enable row level security;
alter table public.user_stats         enable row level security;

-- ── profiles ────────────────────────────────────────────────────────────────
create policy "profiles_select_own" on public.profiles
    for select to authenticated using (id = auth.uid());

create policy "profiles_update_own" on public.profiles
    for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());
-- no insert policy: row is created by the handle_new_user trigger (security definer)
-- no delete policy: account deletion goes through supabase.auth.admin (service role)

-- ── user_word_progress ──────────────────────────────────────────────────────
create policy "uwp_select_own" on public.user_word_progress
    for select to authenticated using (user_id = auth.uid());

create policy "uwp_insert_own" on public.user_word_progress
    for insert to authenticated with check (user_id = auth.uid());

create policy "uwp_update_own" on public.user_word_progress
    for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "uwp_delete_own" on public.user_word_progress
    for delete to authenticated using (user_id = auth.uid());

-- ── review_log (append-only: select + insert; NO update/delete policies) ───
create policy "rl_select_own" on public.review_log
    for select to authenticated using (user_id = auth.uid());

create policy "rl_insert_own" on public.review_log
    for insert to authenticated with check (user_id = auth.uid());
-- history is immutable for clients → prevents tampering with learning stats

-- ── lesson_progress ─────────────────────────────────────────────────────────
create policy "lp_select_own" on public.lesson_progress
    for select to authenticated using (user_id = auth.uid());

create policy "lp_insert_own" on public.lesson_progress
    for insert to authenticated with check (user_id = auth.uid());

create policy "lp_update_own" on public.lesson_progress
    for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ── quiz_attempts (append-only like review_log) ────────────────────────────
create policy "qa_select_own" on public.quiz_attempts
    for select to authenticated using (user_id = auth.uid());

create policy "qa_insert_own" on public.quiz_attempts
    for insert to authenticated with check (user_id = auth.uid());

-- ── user_stats (read-only for the client; writes happen inside the
--    security definer triggers of 00004) ────────────────────────────────────
create policy "us_select_own" on public.user_stats
    for select to authenticated using (user_id = auth.uid());

-- ── storage: public read for content packs only ─────────────────────────────
insert into storage.buckets (id, name, public)
values ('content-packs', 'content-packs', true)
on conflict (id) do nothing;

drop policy if exists "content_packs_public_read" on storage.objects;
create policy "content_packs_public_read" on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'content-packs');
