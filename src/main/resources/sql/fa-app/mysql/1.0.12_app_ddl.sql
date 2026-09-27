-- ------------------------- info -------------------------
-- @@ver: 1_000_012
-- @@info: APK版本增加发布状态
-- ------------------------- info -------------------------

ALTER TABLE `app_apk_version`
  ADD COLUMN `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '发布状态' AFTER `sha256`,
  ADD COLUMN `publish_time` timestamp NULL DEFAULT NULL COMMENT '发布时间' AFTER `status`;

UPDATE `app_apk_version`
SET `publish_time` = `crt_time`
WHERE `status` = 'PUBLISHED' AND `publish_time` IS NULL;

ALTER TABLE `app_apk_version`
  MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态';
