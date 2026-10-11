-- ------------------------- info -------------------------
-- @@ver: 1_000_013
-- @@info: WGT运行时隔离与Vapor原生基座报告
-- ------------------------- info -------------------------

ALTER TABLE "app_release"
  ADD COLUMN "runtime_type" varchar(32) NOT NULL DEFAULT 'APP_PLUS',
  ADD COLUMN "platform" varchar(32) NOT NULL DEFAULT 'APP_PLUS',
  ADD COLUMN "native_base_report_file_id" varchar(64),
  ADD COLUMN "wgt_metadata_json" text,
  ADD COLUMN "wgt_build_report_json" text;
ALTER TABLE "app_release" DROP CONSTRAINT "uk_app_release_version";
ALTER TABLE "app_release" ADD CONSTRAINT "uk_app_release_version" UNIQUE ("app_id","runtime_type","platform","version_code","channel");
ALTER TABLE "app_apk_version" ADD COLUMN "native_base_report_file_id" varchar(64);
COMMENT ON COLUMN "app_release"."runtime_type" IS 'WGT运行时：APP_PLUS/UNI_APP_X_VAPOR';
COMMENT ON COLUMN "app_release"."platform" IS 'WGT目标平台：APP_PLUS/ANDROID';
COMMENT ON COLUMN "app_release"."native_base_report_file_id" IS '已注册的原生APK基座报告文件ID';
COMMENT ON COLUMN "app_release"."wgt_metadata_json" IS '服务端解析的原始WGT元数据';
COMMENT ON COLUMN "app_release"."wgt_build_report_json" IS '经校验的CI构建报告';
COMMENT ON COLUMN "app_apk_version"."native_base_report_file_id" IS '绑定原始APK的Vapor基座报告文件ID';
