-- ------------------------- info -------------------------
-- @@ver: 1_000_010
-- @@info: APK版本增加SHA-256摘要
-- ------------------------- info -------------------------

ALTER TABLE `app_apk_version`
  ADD COLUMN `sha256` varchar(64) DEFAULT NULL COMMENT 'APK文件SHA-256摘要' AFTER `force_update`;
