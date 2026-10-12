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
