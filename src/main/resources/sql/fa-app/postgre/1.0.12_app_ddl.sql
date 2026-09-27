-- ------------------------- info -------------------------
-- @@ver: 1_000_012
-- @@info: APK版本增加发布状态
-- ------------------------- info -------------------------

ALTER TABLE "app_apk_version"
  ADD COLUMN IF NOT EXISTS "status" varchar(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE "app_apk_version"
  ADD COLUMN IF NOT EXISTS "publish_time" timestamp NULL DEFAULT NULL;

COMMENT ON COLUMN "app_apk_version"."status" IS '发布状态';
COMMENT ON COLUMN "app_apk_version"."publish_time" IS '发布时间';

UPDATE "app_apk_version"
SET "publish_time" = "crt_time"
WHERE "status" = 'PUBLISHED' AND "publish_time" IS NULL;

ALTER TABLE "app_apk_version"
  ALTER COLUMN "status" SET DEFAULT 'DRAFT';
