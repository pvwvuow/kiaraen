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
