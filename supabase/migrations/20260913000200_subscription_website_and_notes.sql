-- Migration: 20260913000200_subscription_website_and_notes.sql
-- Description: Adds optional website_url and notes columns to subscriptions table.

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS website_url text;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS notes text;

COMMENT ON COLUMN subscriptions.website_url IS 'Opsiyonel web sitesi / hesap yonetim baglantisi URL adresi.';
COMMENT ON COLUMN subscriptions.notes IS 'Opsiyonel kullanici notlari, plan ve ekran detaylari.';
