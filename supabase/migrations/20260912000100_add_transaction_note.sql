-- transactions tablosuna isteğe bağlı note kolonunun eklenmesi
-- ve sync_write_v2 RPC'sinin note alanını destekleyecek şekilde güncellenmesi.
-- NOT: Bu migration yerel/staging/production ortamlarına otomatik uygulanmaz; açık onay ve manuel kabul bekler.
begin;

alter table public.transactions
    add column if not exists note text;

commit;
