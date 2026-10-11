-- ------------------------- info -------------------------
-- @@ver: 1_000_013
-- @@info: WGT运行时隔离与Vapor原生基座报告
-- ------------------------- info -------------------------

ALTER TABLE `app_release`
  ADD COLUMN `runtime_type` varchar(32) NOT NULL DEFAULT 'APP_PLUS' COMMENT 'WGT运行时：APP_PLUS/UNI_APP_X_VAPOR',
  ADD COLUMN `platform` varchar(32) NOT NULL DEFAULT 'APP_PLUS' COMMENT 'WGT目标平台：APP_PLUS/ANDROID',
  ADD COLUMN `native_base_report_file_id` varchar(64) NULL COMMENT '已注册的原生APK基座报告文件ID',
  ADD COLUMN `wgt_metadata_json` text NULL COMMENT '服务端解析的原始WGT元数据',
  ADD COLUMN `wgt_build_report_json` mediumtext NULL COMMENT '经校验的CI构建报告',
  DROP INDEX `uk_app_release_version`,
  ADD UNIQUE KEY `uk_app_release_version` (`app_id`,`runtime_type`,`platform`,`version_code`,`channel`);
ALTER TABLE `app_apk_version` ADD COLUMN `native_base_report_file_id` varchar(64) NULL COMMENT '绑定原始APK的Vapor基座报告文件ID';
