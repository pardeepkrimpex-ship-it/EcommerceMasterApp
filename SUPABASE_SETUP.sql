-- Run this once in Supabase SQL Editor.
create table if not exists public.records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  type text not null check (type in ('orders','returns')),
  record_date date not null,
  platform text not null,
  courier text,
  count integer not null check (count >= 0),
  created_at timestamptz not null default now()
);

alter table public.records enable row level security;

drop policy if exists "Users can read own records" on public.records;
create policy "Users can read own records" on public.records
for select using (auth.uid() = user_id);

drop policy if exists "Users can insert own records" on public.records;
create policy "Users can insert own records" on public.records
for insert with check (auth.uid() = user_id);

drop policy if exists "Users can delete own records" on public.records;
create policy "Users can delete own records" on public.records
for delete using (auth.uid() = user_id);

create index if not exists records_user_date_idx on public.records(user_id, record_date);
