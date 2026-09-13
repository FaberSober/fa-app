-- ------------------------- info -------------------------
-- @@ver: 1_000_001
-- @@info: 修复crt_time字段更新自动更新
-- ------------------------- info -------------------------

ALTER TABLE "app_apk" ALTER COLUMN "crt_time" SET DATA TYPE timestamp;
ALTER TABLE "app_apk" ALTER COLUMN "crt_time" SET NOT NULL;
ALTER TABLE "app_apk" ALTER COLUMN "crt_time" SET DEFAULT CURRENT_TIMESTAMP;
COMMENT ON COLUMN "app_apk"."crt_time" IS '创建时间';
ALTER TABLE "app_apk_crash" ALTER COLUMN "crt_time" SET DATA TYPE timestamp;
ALTER TABLE "app_apk_crash" ALTER COLUMN "crt_time" SET NOT NULL;
ALTER TABLE "app_apk_crash" ALTER COLUMN "crt_time" SET DEFAULT CURRENT_TIMESTAMP;
COMMENT ON COLUMN "app_apk_crash"."crt_time" IS '创建时间';
ALTER TABLE "app_apk_version" ALTER COLUMN "crt_time" SET DATA TYPE timestamp;
ALTER TABLE "app_apk_version" ALTER COLUMN "crt_time" SET NOT NULL;
ALTER TABLE "app_apk_version" ALTER COLUMN "crt_time" SET DEFAULT CURRENT_TIMESTAMP;
COMMENT ON COLUMN "app_apk_version"."crt_time" IS '创建时间';

ALTER TABLE "app_apk_crash" ALTER COLUMN "crash_time" SET DATA TYPE timestamp;
ALTER TABLE "app_apk_crash" ALTER COLUMN "crash_time" DROP NOT NULL;
ALTER TABLE "app_apk_crash" ALTER COLUMN "crash_time" SET DEFAULT CURRENT_TIMESTAMP;
COMMENT ON COLUMN "app_apk_crash"."crash_time" IS '崩溃时间';

