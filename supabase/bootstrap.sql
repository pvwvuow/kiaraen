-- ═══════════════════════════════════════════════════════════════════════════
-- bootstrap.sql — ONE-FILE setup for a fresh Supabase project
--
-- HOW TO USE (5 minutes):
--   1. Supabase Dashboard → SQL Editor → New query
--   2. Paste this WHOLE file → Run
--   3. Done: content tables + user tables + RLS + stats triggers + sync guards
--
-- Run ONCE on an empty project (it mirrors supabase/migrations/00001..00005).
-- For ongoing changes always use the versioned migrations, not this file.
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- 00001_content_schema.sql
-- Educational CONTENT tables. Read-only for clients (anon + authenticated).
-- Writes happen only through migrations / service_role (CI content loader).
-- User data lives in 00002 and is strictly separated from content.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Levels (A1…C2 — extensible: add a row, no code change) ─────────────────
create table if not exists public.levels (
    id          text primary key,            -- 'A1','A2','B1','B2','C1','C2'
    title       text not null,
    sort_order  int  not null,
    created_at  timestamptz not null default now()
);

-- ── Categories inside a level (professional ordering) ──────────────────────
create table if not exists public.categories (
    id          bigserial primary key,
    level_id    text not null references public.levels(id) on delete cascade,
    key         text not null,               -- stable slug, e.g. 'animals'
    title       text not null,               -- Persian display name
    icon        text not null default 'chat',
    sort_order  int  not null default 0,
    created_at  timestamptz not null default now(),
    unique (level_id, key)
);
create index if not exists idx_categories_level on public.categories (level_id, sort_order);

-- ── Words (one row per headword) ────────────────────────────────────────────
create table if not exists public.words (
    id          bigserial primary key,
    word        text not null,
    phonetic    text,
    pos         text,                        -- n|v|adj|adv|int|prep|phr
    level_id    text not null references public.levels(id),
    category_id bigint not null references public.categories(id),
    difficulty  smallint not null default 3 check (difficulty between 1 and 5),
    created_at  timestamptz not null default now(),
    unique (word, pos)
);
create index if not exists idx_words_level_cat  on public.words (level_id, category_id, id);
create index if not exists idx_words_word_lower on public.words (lower(word) text_pattern_ops);

-- ── Meanings (1..n per word — normalized, no duplicate storage) ────────────
create table if not exists public.word_meanings (
    id          bigserial primary key,
    word_id     bigint not null references public.words(id) on delete cascade,
    meaning_fa  text not null,
    example_en  text,
    example_fa  text,
    sort_order  int  not null default 0
);
create index if not exists idx_meanings_word on public.word_meanings (word_id, sort_order);
-- one meaning per position per word → makes content loader upserts idempotent
create unique index if not exists uq_meaning_position on public.word_meanings (word_id, sort_order);

-- ── Collections (thematic cross-references; a word may be in a level AND any
--    number of collections — many-to-many) ──────────────────────────────────
create table if not exists public.collections (
    id          bigserial primary key,
    slug        text not null unique,
    title       text not null,
    description text,
    sort_order  int  not null default 0,
    created_at  timestamptz not null default now()
);

create table if not exists public.collection_words (
    collection_id bigint not null references public.collections(id) on delete cascade,
    word_id       bigint not null references public.words(id) on delete cascade,
    primary key (collection_id, word_id)
);
create index if not exists idx_collection_words_word on public.collection_words (word_id);

-- ── Lessons (optional course structure over categories) ────────────────────
create table if not exists public.lessons (
    id          bigserial primary key,
    level_id    text not null references public.levels(id),
    category_id bigint references public.categories(id),
    title       text not null,
    sort_order  int  not null default 0,
    created_at  timestamptz not null default now()
);
create index if not exists idx_lessons_level on public.lessons (level_id, sort_order);

create table if not exists public.lesson_words (
    lesson_id bigint not null references public.lessons(id) on delete cascade,
    word_id   bigint not null references public.words(id) on delete cascade,
    position  int not null default 0,
    primary key (lesson_id, word_id)
);

-- ── Quizzes (optional predefined quizzes; the app can also generate MC
--    quizzes locally from the cached content — zero server cost) ────────────
create table if not exists public.quizzes (
    id          bigserial primary key,
    title       text not null,
    level_id    text references public.levels(id),
    category_id bigint references public.categories(id),
    collection_id bigint references public.collections(id),
    quiz_type   text not null default 'mc' check (quiz_type in ('mc','typing','mixed')),
    is_default  boolean not null default false,
    created_at  timestamptz not null default now()
);

create table if not exists public.quiz_questions (
    id         bigserial primary key,
    quiz_id    bigint not null references public.quizzes(id) on delete cascade,
    word_id    bigint not null references public.words(id) on delete cascade,
    options    jsonb not null,               -- ["سیب","پنجره",...]
    answer     text not null,
    position   int not null default 0
);
create index if not exists idx_questions_quiz on public.quiz_questions (quiz_id, position);

-- ── Content pack registry (app checks ONE tiny row instead of re-fetching) ─
create table if not exists public.content_packs (
    version     int primary key,
    storage_path text not null,              -- 'packs/content-v1.json'
    word_count  int not null default 0,
    is_active   boolean not null default false,
    released_at timestamptz not null default now()
);
-- only one active pack at a time
create unique index if not exists uq_one_active_pack on public.content_packs ((is_active)) where is_active;

-- ── RLS: content is world-readable, never client-writable ──────────────────
alter table public.levels           enable row level security;
alter table public.categories       enable row level security;
alter table public.words            enable row level security;
alter table public.word_meanings    enable row level security;
alter table public.collections      enable row level security;
alter table public.collection_words enable row level security;
alter table public.lessons          enable row level security;
alter table public.lesson_words     enable row level security;
alter table public.quizzes          enable row level security;
alter table public.quiz_questions   enable row level security;
alter table public.content_packs    enable row level security;

do $$
declare t text;
begin
    foreach t in array array[
        'levels','categories','words','word_meanings','collections',
        'collection_words','lessons','lesson_words','quizzes','quiz_questions','content_packs'
    ] loop
        execute format(
            'drop policy if exists "content_public_read" on public.%I;', t);
        execute format(
            'create policy "content_public_read" on public.%I for select to anon, authenticated using (true);', t);
    end loop;
end $$;

-- NOTE: no INSERT/UPDATE/DELETE policies exist on content tables on purpose.
-- RLS denies everything not explicitly allowed → clients can never mutate content.


-- ═══════════════════════════════════════════════════════════════════════════
-- 00002_user_schema.sql
-- USER-OWNED data. Every table carries user_id and is protected by RLS (00003).
-- Design notes
--   * user_word_progress  = current STATE per (user, word) — upserted, LWW-merged
--   * review_log          = append-only EVENT log (source of truth for stats)
--                           client_event_id makes retries idempotent
--   * lesson_progress     = state per (user, lesson)
--   * quiz_attempts       = append-only summary events (answers flow via review_log)
--   * user_stats          = single-row CACHE maintained by triggers (00004);
--                           rebuildable any time via recalc_user_stats()
--   * v_user_daily_activity = derived view — no duplicate daily rows stored
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Profiles (1:1 with auth.users; created by trigger, never by client) ────
create table if not exists public.profiles (
    id           uuid primary key references auth.users(id) on delete cascade,
    username     text unique,
    display_name text,
    avatar_url   text,
    native_lang  text not null default 'fa',
    daily_goal   int  not null default 10 check (daily_goal between 1 and 200),
    tz_offset_minutes int not null default 210,   -- +3:30 Tehran; used for local-day streaks
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- ── Word progress (STATE) ───────────────────────────────────────────────────
create table if not exists public.user_word_progress (
    user_id          uuid not null references auth.users(id) on delete cascade,
    word_id          bigint not null references public.words(id) on delete cascade,
    status           text not null default 'new'
                     check (status in ('new','learning','review','mastered')),
    familiarity      smallint not null default 0 check (familiarity between 0 and 5),
    correct_count    int not null default 0 check (correct_count >= 0),
    wrong_count      int not null default 0 check (wrong_count >= 0),
    -- SRS-lite fields (SM-2 ready; used by app now, server may retune later)
    ease_factor      real not null default 2.5 check (ease_factor >= 1.3 and ease_factor <= 3.0),
    interval_days    real not null default 0 check (interval_days >= 0),
    repetitions      int not null default 0 check (repetitions >= 0),
    due_at           timestamptz not null default now(),
    last_reviewed_at timestamptz,
    learned_at       timestamptz,
    source           text not null default 'lesson' check (source in ('lesson','quiz','review','manual','collection')),
    -- sync bookkeeping
    client_updated_at timestamptz not null default now(),   -- device clock of last change (LWW key)
    server_updated_at timestamptz not null default now(),
    primary key (user_id, word_id)
);
create index if not exists idx_uwp_queue   on public.user_word_progress (user_id, due_at) where status <> 'new';
create index if not exists idx_uwp_status  on public.user_word_progress (user_id, status);
create index if not exists idx_uwp_pending on public.user_word_progress (client_updated_at);

-- ── Review log (EVENTS — append-only, idempotent via client_event_id) ──────
create table if not exists public.review_log (
    id              bigserial primary key,
    user_id         uuid not null references auth.users(id) on delete cascade,
    word_id         bigint not null references public.words(id) on delete cascade,
    result          text not null check (result in ('correct','wrong','easy','hard','again')),
    mode            text not null default 'flashcard'
                    check (mode in ('flashcard','quiz','typing','listening','manual')),
    quiz_id         bigint references public.quizzes(id) on delete set null,
    xp              smallint not null default 0,
    client_event_id uuid not null,          -- generated ON DEVICE → safe retries
    created_at      timestamptz not null default now(),
    unique (user_id, client_event_id)
);
create index if not exists idx_review_log_user_time on public.review_log (user_id, created_at desc);
create index if not exists idx_review_log_user_word on public.review_log (user_id, word_id);

-- ── Lesson progress (STATE) ─────────────────────────────────────────────────
create table if not exists public.lesson_progress (
    user_id           uuid not null references auth.users(id) on delete cascade,
    lesson_id         bigint not null references public.lessons(id) on delete cascade,
    status            text not null default 'in_progress'
                      check (status in ('in_progress','completed')),
    score             smallint not null default 0 check (score between 0 and 100),
    client_updated_at timestamptz not null default now(),
    server_updated_at timestamptz not null default now(),
    completed_at      timestamptz,
    primary key (user_id, lesson_id)
);

-- ── Quiz attempts (EVENTS — summary only; per-answer facts live in review_log)
create table if not exists public.quiz_attempts (
    id              bigserial primary key,
    user_id         uuid not null references auth.users(id) on delete cascade,
    quiz_id         bigint references public.quizzes(id) on delete set null,
    quiz_ref        text,                    -- client-side generated quiz ref, e.g. 'cat:12:auto'
    score           int not null default 0 check (score >= 0),
    total           int not null default 0 check (total >= 0),
    duration_ms     int not null default 0,
    client_event_id uuid not null,
    created_at      timestamptz not null default now(),
    unique (user_id, client_event_id)
);
create index if not exists idx_attempts_user_time on public.quiz_attempts (user_id, created_at desc);

-- ── User stats (CACHE — trigger-maintained, rebuildable) ───────────────────
create table if not exists public.user_stats (
    user_id           uuid primary key references auth.users(id) on delete cascade,
    total_reviews     int not null default 0,
    total_correct     int not null default 0,
    total_learned     int not null default 0,   -- status in ('review','mastered') AND learned_at not null
    total_mastered    int not null default 0,
    current_streak    int not null default 0,
    longest_streak    int not null default 0,
    last_activity_date date,                    -- UTC date (client tz applied via profile offset v2)
    total_xp          int not null default 0,
    total_quiz_attempts int not null default 0,
    updated_at        timestamptz not null default now()
);

-- ── Daily activity (DERIVED — a view, not stored rows; zero duplication) ───
create or replace view public.v_user_daily_activity
with (security_invoker = true) as
select
    user_id,
    (created_at at time zone 'utc')::date as day,
    count(*)                                          as reviews,
    count(*) filter (where result in ('correct','easy')) as correct,
    count(*) filter (where result in ('wrong','again','hard')) as wrong,
    coalesce(sum(xp), 0)                              as xp
from public.review_log
group by user_id, 2;

-- ── Auto-create profile + stats row for every new auth user ────────────────
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, display_name)
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'display_name', ''),
            split_part(new.email, '@', 1)
        )
    )
    on conflict (id) do nothing;

    insert into public.user_stats (user_id) values (new.id)
    on conflict (user_id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();


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


-- ═══════════════════════════════════════════════════════════════════════════
-- 00004_stats_triggers.sql
-- Server-side maintenance of user_stats (a CACHE over event tables):
--   * review_log insert        → reviews/correct/xp counters + streak days
--   * quiz_attempts insert     → total_quiz_attempts
--   * user_word_progress I/U   → total_learned / total_mastered counters
-- plus recalc_user_stats(): full rebuild for repairs (self-service, guarded).
-- Keeping aggregates on the server avoids COUNT(*) scans by the app at scale.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── review events → counters + streak ──────────────────────────────────────
create or replace function public.bump_stats_on_review()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user            uuid := new.user_id;
    d                 date := (new.created_at at time zone 'utc')::date;
    v_streak          int;
    v_longest         int;
    v_last_day        date;
    v_total_reviews   int;
    v_total_correct   int;
    v_total_xp        int;
begin
    select coalesce(current_streak, 0), coalesce(longest_streak, 0), last_activity_date,
           coalesce(total_reviews, 0), coalesce(total_correct, 0), coalesce(total_xp, 0)
      into v_streak, v_longest, v_last_day, v_total_reviews, v_total_correct, v_total_xp
    from public.user_stats
    where user_id = v_user
    for update;

    if v_total_reviews is null then
        -- stats row missing (e.g. user created before trigger existed)
        insert into public.user_stats (user_id) values (v_user)
        on conflict (user_id) do nothing;
        v_streak := 0; v_longest := 0; v_last_day := null;
        v_total_reviews := 0; v_total_correct := 0; v_total_xp := 0;
    end if;

    if v_last_day is null then
        v_streak := 1;
    elsif d = v_last_day then
        null;                       -- same day → streak unchanged
    elsif d = v_last_day + 1 then
        v_streak := v_streak + 1;
    elsif d > v_last_day then
        v_streak := 1;              -- gap → streak restarts
    else
        null;                       -- out-of-order older event → keep as-is
    end if;

    v_last_day      := greatest(coalesce(v_last_day, d), d);
    v_longest       := greatest(v_longest, v_streak);
    v_total_reviews := v_total_reviews + 1;
    if new.result in ('correct','easy') then
        v_total_correct := v_total_correct + 1;
    end if;
    v_total_xp := v_total_xp + new.xp;

    insert into public.user_stats (
        user_id, total_reviews, total_correct, current_streak, longest_streak,
        last_activity_date, total_xp, updated_at
    ) values (
        v_user, v_total_reviews, v_total_correct, v_streak, v_longest,
        v_last_day, v_total_xp, now()
    )
    on conflict (user_id) do update set
        total_reviews      = excluded.total_reviews,
        total_correct      = excluded.total_correct,
        current_streak     = excluded.current_streak,
        longest_streak     = excluded.longest_streak,
        last_activity_date = excluded.last_activity_date,
        total_xp           = excluded.total_xp,
        updated_at         = now();

    return new;
end;
$$;

drop trigger if exists trg_review_bump_stats on public.review_log;
create trigger trg_review_bump_stats
    after insert on public.review_log
    for each row execute function public.bump_stats_on_review();

-- ── quiz attempts → attempt counter ─────────────────────────────────────────
create or replace function public.bump_stats_on_attempt()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.user_stats
       set total_quiz_attempts = total_quiz_attempts + 1,
           updated_at = now()
     where user_id = new.user_id;
    return new;
end;
$$;

drop trigger if exists trg_attempt_bump_stats on public.quiz_attempts;
create trigger trg_attempt_bump_stats
    after insert on public.quiz_attempts
    for each row execute function public.bump_stats_on_attempt();

-- ── word progress → learned / mastered counters ────────────────────────────
create or replace function public.bump_stats_on_progress()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    was_learned  boolean;
    is_learned   boolean;
    was_mastered boolean;
    is_mastered  boolean;
begin
    if tg_op = 'UPDATE' then
        was_learned  := (old.learned_at is not null);
        was_mastered := (old.status = 'mastered');
    else
        was_learned  := false;
        was_mastered := false;
    end if;

    is_learned  := (new.learned_at is not null);
    is_mastered := (new.status = 'mastered');

    if tg_op = 'UPDATE' and was_learned = is_learned and was_mastered = is_mastered then
        return new;   -- nothing relevant changed
    end if;

    update public.user_stats
       set total_learned  = total_learned
                            + (case when is_learned and not was_learned then 1 else 0 end)
                            - (case when was_learned and not is_learned then 1 else 0 end),
           total_mastered = total_mastered
                            + (case when is_mastered and not was_mastered then 1 else 0 end)
                            - (case when was_mastered and not is_mastered then 1 else 0 end),
           updated_at = now()
     where user_id = new.user_id;

    return new;
end;
$$;

drop trigger if exists trg_progress_bump_stats on public.user_word_progress;
create trigger trg_progress_bump_stats
    after insert or update on public.user_word_progress
    for each row execute function public.bump_stats_on_progress();

-- ── full rebuild (repairs / backfills); callable only for own user_id ──────
create or replace function public.recalc_user_stats(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    caller        uuid := auth.uid();
    v_reviews     int;
    v_correct     int;
    v_xp          int;
    v_last_day    date;
    v_longest     int := 0;
    v_current     int := 0;
    v_learned     int;
    v_mastered    int;
begin
    if caller is null or caller <> p_user_id then
        raise exception 'recalc_user_stats: not allowed for %', caller;
    end if;

    select count(*),
           count(*) filter (where result in ('correct','easy')),
           coalesce(sum(xp), 0),
           max((created_at at time zone 'utc')::date)
      into v_reviews, v_correct, v_xp, v_last_day
    from public.review_log
    where user_id = p_user_id;

    -- gaps-and-islands over active days: consecutive dates share one group key
    with days as (
        select distinct (created_at at time zone 'utc')::date as d
        from public.review_log
        where user_id = p_user_id
    ),
    runs as (
        select d, (d - (row_number() over (order by d))::int) as grp
        from days
    ),
    run_lens as (
        select grp, count(*) as len, max(d) as run_end from runs group by grp
    )
    select
        coalesce(max(len), 0),
        coalesce((select len from run_lens
                  where run_end = (select max(d) from days)
                  order by len desc limit 1), 0)
      into v_longest, v_current
    from run_lens;

    select count(*) filter (where learned_at is not null),
           count(*) filter (where status = 'mastered')
      into v_learned, v_mastered
    from public.user_word_progress
    where user_id = p_user_id;

    insert into public.user_stats (
        user_id, total_reviews, total_correct, total_learned, total_mastered,
        current_streak, longest_streak, last_activity_date, total_xp, updated_at
    ) values (
        p_user_id,
        coalesce(v_reviews, 0), coalesce(v_correct, 0),
        coalesce(v_learned, 0), coalesce(v_mastered, 0),
        v_current, v_longest, v_last_day, coalesce(v_xp, 0), now()
    )
    on conflict (user_id) do update set
        total_reviews      = excluded.total_reviews,
        total_correct      = excluded.total_correct,
        total_learned      = excluded.total_learned,
        total_mastered     = excluded.total_mastered,
        current_streak     = excluded.current_streak,
        longest_streak     = excluded.longest_streak,
        last_activity_date = excluded.last_activity_date,
        total_xp           = excluded.total_xp,
        updated_at         = now();
end;
$$;

-- note: recalc_user_stats writes user_stats directly as SECURITY DEFINER
-- (table owner), bypassing the missing client UPDATE policy by design.


-- ═══════════════════════════════════════════════════════════════════════════
-- 00005_sync_guards.sql
-- Server-side Last-Write-Wins guard for STATE tables pushed from devices.
--   * STATE tables (user_word_progress, lesson_progress) are upserted by the
--     app. A retried/older batch must never overwrite a newer change from
--     another device → stale writes are silently skipped.
--   * EVENT tables (review_log, quiz_attempts) need no guard: retries are
--     deduplicated by unique (user_id, client_event_id).
-- Known trade-off: device clocks may skew. We clamp the future bound here and
-- plan HLC (hybrid logical clocks) in docs/04-sync-offline.md (Phase 5).
-- ═══════════════════════════════════════════════════════════════════════════

create or replace function public.lww_guard()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'UPDATE' then
        if new.client_updated_at <= old.client_updated_at then
            return null;                    -- stale write from another device → ignore
        end if;
    end if;
    -- bound device-clock skew: never trust a timestamp too far in the future
    new.client_updated_at := least(new.client_updated_at, now() + interval '5 minutes');
    new.server_updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_uwp_lww on public.user_word_progress;
create trigger trg_uwp_lww
    before insert or update on public.user_word_progress
    for each row execute function public.lww_guard();

drop trigger if exists trg_lp_lww on public.lesson_progress;
create trigger trg_lp_lww
    before insert or update on public.lesson_progress
    for each row execute function public.lww_guard();
