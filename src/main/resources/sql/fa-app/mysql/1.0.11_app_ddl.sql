-- ------------------------- info -------------------------
-- @@ver: 1_000_011
-- @@info: APK表增加DCloud AppID
-- ------------------------- info -------------------------

ALTER TABLE `app_apk`
  ADD COLUMN `dcloud_app_id` varchar(128) DEFAULT NULL COMMENT 'DCloud AppID' AFTER `application_id`;

CREATE UNIQUE INDEX `uk_app_apk_dcloud_app_id` ON `app_apk` (`dcloud_app_id`);
