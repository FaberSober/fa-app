-- ------------------------- info -------------------------
-- @@ver: 1_000_009
-- @@info: 支持同一发布版本的多基准WGT包
-- ------------------------- info -------------------------

ALTER TABLE `app_release_package`
  DROP INDEX `uk_app_release_package`,
  ADD COLUMN `active_release_id` bigint(20) unsigned
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `release_id` ELSE NULL END) STORED,
  ADD COLUMN `base_version_key` bigint(20)
    GENERATED ALWAYS AS (IFNULL(`base_version_code`, 0)) STORED,
  ADD UNIQUE KEY `uk_app_release_package_active`
    (`active_release_id`, `platform`, `package_type`, `base_version_key`);
