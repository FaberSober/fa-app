-- ------------------------- info -------------------------
-- @@ver: 1_000_011
-- @@info: APK表增加DCloud AppID
-- ------------------------- info -------------------------

ALTER TABLE "app_apk" ADD COLUMN IF NOT EXISTS "dcloud_app_id" varchar(128);
COMMENT ON COLUMN "app_apk"."dcloud_app_id" IS 'DCloud AppID';

CREATE UNIQUE INDEX IF NOT EXISTS "uk_app_apk_dcloud_app_id" ON "app_apk" ("dcloud_app_id");
