-- ------------------------- info -------------------------
-- @@ver: 1_000_008
-- @@info: 增加应用版本灰度发布与自动回滚配置
-- ------------------------- info -------------------------

ALTER TABLE `app_release`
  ADD COLUMN `rollout_percent` int NOT NULL DEFAULT 100 COMMENT '灰度比例，0-100' AFTER `min_supported_version_code`,
  ADD COLUMN `target_device_ids` text NULL COMMENT '目标安装标识白名单，逗号或换行分隔' AFTER `rollout_percent`,
  ADD COLUMN `auto_rollback` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动回滚' AFTER `target_device_ids`,
  ADD COLUMN `rollback_error_threshold` int NOT NULL DEFAULT 10 COMMENT '自动回滚异常数量阈值' AFTER `auto_rollback`,
  ADD COLUMN `rollback_window_minutes` int NOT NULL DEFAULT 15 COMMENT '自动回滚统计窗口，单位分钟' AFTER `rollback_error_threshold`;

ALTER TABLE `app_release`
  ADD KEY `idx_app_release_rollback` (`status`,`auto_rollback`);
