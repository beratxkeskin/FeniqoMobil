-- TASLAK: Sync Write V2 Idempotency Receipt Tablosu. Yalnız yerel/staging için.
begin;

-- pgcrypto eklentisinin extensions şemasında hazır olduğunu doğrula
create extension if not exists pgcrypto with schema extensions;

-- V2 Idempotency Receipt Tablosu
create table if not exists public.sync_operations_receipts (
    operation_id text not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    entity_type text not null,
    entity_id uuid not null,
    operation_type text not null,
    base_version bigint,
    request_fingerprint text not null,
    result_status text not null,
    applied_version bigint not null,
    response_record jsonb not null,
    applied_at timestamptz not null default timezone('utc'::text, now()),

    constraint sync_operations_receipts_pkey
        primary key (user_id, operation_id),

    constraint sync_operations_receipts_operation_id_check
        check (operation_id ~ '^[0-9a-f]{32}$'),

    constraint sync_operations_receipts_entity_type_check
        check (entity_type in ('PROFILE', 'CATEGORY', 'TRANSACTION')),

    constraint sync_operations_receipts_operation_type_check
        check (operation_type in ('CREATE', 'UPDATE', 'DELETE')),

    constraint sync_operations_receipts_base_version_check
        check (
            (operation_type = 'CREATE' and base_version is null)
            or (operation_type in ('UPDATE', 'DELETE') and base_version >= 1)
        ),

    constraint sync_operations_receipts_request_fingerprint_check
        check (request_fingerprint ~ '^[0-9a-f]{64}$'),

    constraint sync_operations_receipts_result_status_check
        check (result_status = 'APPLIED'),

    constraint sync_operations_receipts_applied_version_check
        check (applied_version >= 1),

    constraint sync_operations_receipts_response_record_check
        check (jsonb_typeof(response_record) = 'object')
);

-- Tablo üzerinde satır düzeyinde güvenlik (RLS) etkinleştir (hiçbir policy tanımlanmaz)
alter table public.sync_operations_receipts enable row level security;

-- public, anon ve authenticated rollerinden doğrudan erişim izinlerini tamamen kaldır
revoke all on table public.sync_operations_receipts from public, anon, authenticated;

-- Opsiyonel sorgu ve bakım indeksleri
create index if not exists idx_sync_operations_receipts_user_entity
    on public.sync_operations_receipts (user_id, entity_type, entity_id);

create index if not exists idx_sync_operations_receipts_user_applied_at
    on public.sync_operations_receipts (user_id, applied_at desc);

commit;
