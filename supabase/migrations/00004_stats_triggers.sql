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
