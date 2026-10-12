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
