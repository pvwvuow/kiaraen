-- ═══════════════════════════════════════════════════════════════════════════
-- supabase/tests/rls_tests.sql   (pgTAP — run with:  supabase test db)
-- Verifies the security fence: users can ONLY touch their own rows,
-- content is world-readable, event history is immutable for clients.
-- Requires two test users created beforehand (see header of each block).
-- ═══════════════════════════════════════════════════════════════════════════
begin;
select plan(10);

-- fixture users (idempotent)
do $$
declare
    u1 uuid;
    u2 uuid;
begin
    select id into u1 from auth.users where email = 'rls-test-1@example.com';
    if u1 is null then
        insert into auth.users (instance_id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token, recovery_token)
        values ('00000000-0000-0000-0000-000000000000', 'rls-test-1@example.com', 'x', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now(), '', '')
        returning id into u1;
    end if;

    select id into u2 from auth.users where email = 'rls-test-2@example.com';
    if u2 is null then
        insert into auth.users (instance_id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token, recovery_token)
        values ('00000000-0000-0000-0000-000000000000', 'rls-test-2@example.com', 'x', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now(), '', '')
        returning id into u2;
    end if;
end $$;

create or replace function _set_jwt(uid uuid) returns void language plpgsql as $$
begin
    execute format('set local request.jwt.claims = %L',
                   json_build_object('sub', uid, 'role', 'authenticated')::text);
end $$;

-- a level for content fixtures
insert into public.levels (id, title, sort_order) values ('TA', 'RLS Test Level', 99)
on conflict (id) do nothing;
insert into public.categories (level_id, key, title, sort_order)
select 'TA', 'rls_test', 'RLS Test Cat', 99
where not exists (select 1 from public.categories where level_id = 'TA' and key = 'rls_test');
insert into public.words (word, pos, level_id, category_id)
select 'rlstestword', 'n', 'TA', c.id from public.categories c where c.level_id = 'TA' and c.key = 'rls_test'
on conflict (word, pos) do nothing;

-- 1. anon can read content
set local role anon;
select is((select count(*) from public.levels where id = 'TA'), 1::bigint, 'anon can read levels');

-- 2. anon cannot read profiles
select is((select count(*) from public.profiles), 0::bigint, 'anon cannot read profiles');

-- 3-4. user1 sees own progress, not user2's
set local role authenticated;
select _set_jwt((select id from auth.users where email = 'rls-test-1@example.com'));
insert into public.user_word_progress (user_id, word_id, status)
select (select id from auth.users where email = 'rls-test-1@example.com'), w.id, 'learning'
from public.words w where w.word = 'rlstestword'
on conflict (user_id, word_id) do nothing;

select is((select count(*) from public.user_word_progress), 1::bigint, 'user1 sees own progress row');
select _set_jwt((select id from auth.users where email = 'rls-test-2@example.com'));
select is((select count(*) from public.user_word_progress), 0::bigint, 'user2 cannot see user1 progress');

-- 5. user2 cannot insert progress for user1 (with check blocks cross-user writes)
select _set_jwt((select id from auth.users where email = 'rls-test-2@example.com'));
select throws_ok(
    $q$ insert into public.user_word_progress (user_id, word_id)
        select (select id from auth.users where email = 'rls-test-1@example.com'), w.id
        from public.words w where w.word = 'rlstestword' $q$,
    '42501',
    'cross-user progress insert must be denied'
);

-- 6-7. review_log: insert own ok with client_event_id; cross-user insert denied
select _set_jwt((select id from auth.users where email = 'rls-test-2@example.com'));
insert into public.review_log (user_id, word_id, result, mode, client_event_id)
select (select id from auth.users where email = 'rls-test-2@example.com'), w.id, 'correct', 'quiz', gen_random_uuid()
from public.words w where w.word = 'rlstestword';
select is((select count(*) from public.review_log), 1::bigint, 'user2 inserted own review event');

select throws_ok(
    $q$ insert into public.review_log (user_id, word_id, result, mode, client_event_id)
        select (select id from auth.users where email = 'rls-test-1@example.com'), w.id, 'correct', 'quiz', gen_random_uuid()
        from public.words w where w.word = 'rlstestword' $q$,
    '42501',
    'cross-user review insert must be denied'
);

-- 8. review_log is append-only: UPDATE denied (no policy)
select throws_ok(
    $q$ update public.review_log set result = 'easy' $q$,
    '42501',
    'review_log must be immutable for clients'
);

-- 9. duplicate client_event_id is rejected (retry idempotency fence)
select _set_jwt((select id from auth.users where email = 'rls-test-2@example.com'));
insert into public.review_log (user_id, word_id, result, mode, client_event_id)
select (select id from auth.users where email = 'rls-test-2@example.com'), w.id, 'correct', 'quiz',
       '11111111-1111-1111-1111-111111111111'::uuid
from public.words w where w.word = 'rlstestword';

select throws_ok(
    $q$ insert into public.review_log (user_id, word_id, result, mode, client_event_id)
        select (select id from auth.users where email = 'rls-test-2@example.com'), w.id, 'correct', 'quiz',
               '11111111-1111-1111-1111-111111111111'::uuid
        from public.words w where w.word = 'rlstestword' $q$,
    '23505',
    'same client_event_id twice must be rejected'
);

-- 10. user cannot read another user's review_log
select _set_jwt((select id from auth.users where email = 'rls-test-1@example.com'));
select is((select count(*) from public.review_log), 0::bigint, 'user1 cannot see user2 review events');

select * from finish();
rollback;
