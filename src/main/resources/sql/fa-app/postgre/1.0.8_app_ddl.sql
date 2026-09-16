-- ------------------------- info -------------------------
-- @@ver: 1_000_008
-- @@info: 增加应用版本灰度发布与自动回滚配置
-- ------------------------- info -------------------------

ALTER TABLE "app_release" ADD COLUMN "rollout_percent" integer NOT NULL DEFAULT 100;
COMMENT ON COLUMN "app_release"."rollout_percent" IS '灰度比例，0-100';
ALTER TABLE "app_release" ADD COLUMN "target_device_ids" text;
COMMENT ON COLUMN "app_release"."target_device_ids" IS '目标安装标识白名单，逗号或换行分隔';
ALTER TABLE "app_release" ADD COLUMN "auto_rollback" boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN "app_release"."auto_rollback" IS '是否自动回滚';
ALTER TABLE "app_release" ADD COLUMN "rollback_error_threshold" integer NOT NULL DEFAULT 10;
COMMENT ON COLUMN "app_release"."rollback_error_threshold" IS '自动回滚异常数量阈值';
ALTER TABLE "app_release" ADD COLUMN "rollback_window_minutes" integer NOT NULL DEFAULT 15;
COMMENT ON COLUMN "app_release"."rollback_window_minutes" IS '自动回滚统计窗口，单位分钟';

CREATE INDEX "idx_app_release_rollback" ON "app_release" ("status", "auto_rollback");
