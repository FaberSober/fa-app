-- ------------------------- info -------------------------
-- @@ver: 1_000_009
-- @@info: 支持同一发布版本的多基准WGT包
-- ------------------------- info -------------------------

ALTER TABLE "app_release_package" DROP CONSTRAINT "uk_app_release_package";

CREATE UNIQUE INDEX "uk_app_release_package_active"
  ON "app_release_package" ("release_id", "platform", "package_type", COALESCE("base_version_code", 0))
  WHERE "deleted" = FALSE;
