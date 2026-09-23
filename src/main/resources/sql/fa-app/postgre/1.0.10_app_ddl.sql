-- ------------------------- info -------------------------
-- @@ver: 1_000_010
-- @@info: APK版本增加SHA-256摘要
-- ------------------------- info -------------------------

ALTER TABLE "app_apk_version"
  ADD COLUMN IF NOT EXISTS "sha256" varchar(64);

COMMENT ON COLUMN "app_apk_version"."sha256" IS 'APK文件SHA-256摘要';
