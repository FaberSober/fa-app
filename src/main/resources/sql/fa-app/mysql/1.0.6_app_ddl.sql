-- ------------------------- info -------------------------
-- @@ver: 1_000_006
-- @@info: 增加通用应用版本发布模型
-- ------------------------- info -------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- Table structure for app_release
-- ----------------------------

CREATE TABLE IF NOT EXISTS `app_release` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `app_id` int(11) unsigned NOT NULL COMMENT '应用ID，关联app_apk.id',
  `version_name` varchar(64) NOT NULL COMMENT '版本名称',
  `version_code` bigint(20) NOT NULL COMMENT '版本编码',
  `channel` varchar(32) NOT NULL DEFAULT 'stable' COMMENT '发布渠道',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT/PUBLISHED/REVOKED',
  `force_update` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否强制更新',
  `min_supported_version_code` bigint(20) DEFAULT NULL COMMENT '最低支持版本编码',
  `release_note` text COMMENT '更新说明',
  `publish_time` timestamp NULL DEFAULT NULL COMMENT '发布时间',
  `crt_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `crt_user` varchar(32) NOT NULL COMMENT '创建用户ID',
  `crt_name` varchar(255) NOT NULL COMMENT '创建用户',
  `crt_host` varchar(255) DEFAULT NULL COMMENT '创建IP',
  `upd_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `upd_user` varchar(32) DEFAULT NULL COMMENT '更新用户ID',
  `upd_name` varchar(255) DEFAULT NULL COMMENT '更新用户',
  `upd_host` varchar(255) DEFAULT NULL COMMENT '更新IP',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_app_release_version` (`app_id`,`version_code`,`channel`),
  KEY `idx_app_release_query` (`app_id`,`channel`,`status`,`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='应用版本发布记录';

-- ----------------------------
-- Table structure for app_release_package
-- ----------------------------

CREATE TABLE IF NOT EXISTS `app_release_package` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `release_id` bigint(20) unsigned NOT NULL COMMENT '版本发布ID',
  `platform` varchar(32) NOT NULL COMMENT '发布平台：ANDROID/IOS/APP_PLUS/MP_WEIXIN/H5',
  `package_type` varchar(16) NOT NULL COMMENT '包类型：APK/IPA/WGT/FULL',
  `base_version_code` bigint(20) DEFAULT NULL COMMENT '增量包基础版本编码',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID',
  `size` bigint(20) DEFAULT NULL COMMENT '文件大小，单位字节',
  `sha256` varchar(64) NOT NULL COMMENT '文件SHA-256摘要',
  `crt_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `crt_user` varchar(32) NOT NULL COMMENT '创建用户ID',
  `crt_name` varchar(255) NOT NULL COMMENT '创建用户',
  `crt_host` varchar(255) DEFAULT NULL COMMENT '创建IP',
  `upd_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `upd_user` varchar(32) DEFAULT NULL COMMENT '更新用户ID',
  `upd_name` varchar(255) DEFAULT NULL COMMENT '更新用户',
  `upd_host` varchar(255) DEFAULT NULL COMMENT '更新IP',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_app_release_package` (`release_id`,`platform`,`package_type`),
  KEY `idx_app_release_package_query` (`release_id`,`platform`,`package_type`,`base_version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='应用版本发布包';
