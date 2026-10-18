-- =====================================================================
-- Streamly — Supabase schema (Postgres). Run once in: SQL Editor → New query
-- =====================================================================
create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

-- ---------- Admin allowlist (SERVER-SIDE; no client can read/write it) ----------
create table if not exists admin_allowlist (email text primary key);
insert into admin_allowlist (email) values ('alsamimhossain83@gmail.com') on conflict do nothing;
alter table admin_allowlist enable row level security;   -- no policies => no access via API

-- Verified identity comes from the signed JWT (Google OAuth / email magic link).
create or replace function is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from admin_allowlist
    where email = lower(coalesce(auth.jwt() ->> 'email', ''))
  );
$$;
grant execute on function is_admin() to anon, authenticated;

-- ---------- Tables ----------
create table if not exists categories (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique,
  description text default '',
  thumb_url text default '',
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists videos (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 200),
  slug text not null unique,
  description text default '',
  category_id uuid references categories(id) on delete restrict,
  tags text[] not null default '{}',
  source_url text default '',
  source_type text not null default 'mp4' check (source_type in ('mp4','hls')),
  storage_path text,
  thumb_url text default '',
  thumb_path text,
  status text not null default 'draft' check (status in ('draft','processing','published','failed')),
  featured boolean not null default false,
  trending boolean not null default false,
  publish_at timestamptz not null default now(),
  views bigint not null default 0,
  duration int,
  created_at timestamptz not null default now(),
  -- a video can't be published without a source
  constraint published_needs_source check (status <> 'published' or char_length(source_url) > 0)
);
create index if not exists videos_pub_idx   on videos (status, publish_at desc);
create index if not exists videos_views_idx on videos (views desc);
create index if not exists videos_cat_idx   on videos (category_id);
create index if not exists videos_title_trgm on videos using gin (title gin_trgm_ops);

create table if not exists video_views (
  id bigserial primary key,
  video_id uuid not null references videos(id) on delete cascade,
  viewer_key text not null,
  created_at timestamptz not null default now()
);
create index if not exists vv_lookup_idx on video_views (video_id, viewer_key, created_at desc);
create index if not exists vv_time_idx   on video_views (created_at);

create table if not exists watch_history (
  user_id uuid not null references auth.users(id) on delete cascade,
  video_id uuid not null references videos(id) on delete cascade,
  position_sec int not null default 0,
  duration int not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, video_id)
);

create table if not exists watchlist (
  user_id uuid not null references auth.users(id) on delete cascade,
  video_id uuid not null references videos(id) on delete cascade,
  kind text not null check (kind in ('saved','later')),
  created_at timestamptz not null default now(),
  primary key (user_id, video_id, kind)
);

create table if not exists site_settings (key text primary key, value jsonb not null default '{}');
insert into site_settings (key, value) values
  ('general', '{"name":"Streamly","desc":"আধুনিক ভিডিও স্ট্রিমিং","contact":"","theme":"dark","logo":"","favicon":""}'),
  ('home',    '{"cats":[]}')
on conflict do nothing;

create table if not exists admin_logs (
  id bigserial primary key,
  admin_email text not null,
  action text not null,
  detail jsonb default '{}',
  created_at timestamptz not null default now()
);

-- ---------- View counting (deduplicated, server-side) ----------
-- Same viewer_key + same video within 30 minutes counts once.
create or replace function record_view(p_video uuid, p_key text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_key is null or char_length(p_key) not between 8 and 64 then return; end if;
  if not exists (select 1 from videos where id = p_video and status = 'published' and publish_at <= now()) then return; end if;
  if exists (select 1 from video_views where video_id = p_video and viewer_key = p_key
             and created_at > now() - interval '30 minutes') then return; end if;
  insert into video_views (video_id, viewer_key) values (p_video, p_key);
  update videos set views = views + 1 where id = p_video;
end $$;
grant execute on function record_view(uuid, text) to anon, authenticated;

-- ---------- Row Level Security ----------
alter table categories    enable row level security;
alter table videos        enable row level security;
alter table video_views   enable row level security;
alter table watch_history enable row level security;
alter table watchlist     enable row level security;
alter table site_settings enable row level security;
alter table admin_logs    enable row level security;

create policy cat_read   on categories for select using (true);
create policy cat_write  on categories for all using (is_admin()) with check (is_admin());

create policy vid_read   on videos for select using ((status = 'published' and publish_at <= now()) or is_admin());
create policy vid_write  on videos for all using (is_admin()) with check (is_admin());

create policy vv_admin   on video_views for select using (is_admin());   -- inserts only via record_view()

create policy wh_own     on watch_history for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy wl_own     on watchlist     for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy set_read   on site_settings for select using (true);
create policy set_write  on site_settings for all using (is_admin()) with check (is_admin());

create policy log_admin  on admin_logs for all using (is_admin()) with check (is_admin());

-- ---------- Storage ----------
insert into storage.buckets (id, name, public) values ('videos','videos',true), ('thumbs','thumbs',true)
on conflict (id) do nothing;

create policy "public read media" on storage.objects for select using (bucket_id in ('videos','thumbs'));
create policy "admin insert media" on storage.objects for insert with check (bucket_id in ('videos','thumbs') and is_admin());
create policy "admin update media" on storage.objects for update using (bucket_id in ('videos','thumbs') and is_admin());
create policy "admin delete media" on storage.objects for delete using (bucket_id in ('videos','thumbs') and is_admin());
