-- ------------------------- info -------------------------
-- @@ver: 1_000_005
-- @@info: 增加Desktop客户端版本发布模型
-- ------------------------- info -------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- Table structure for app_client
-- ----------------------------

CREATE TABLE IF NOT EXISTS `app_client` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `client_code` varchar(64) NOT NULL COMMENT '客户端标识',
  `name` varchar(255) NOT NULL COMMENT '客户端名称',
  `identifier` varchar(255) NOT NULL COMMENT '客户端应用标识',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `remark` text COMMENT '备注',
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
  UNIQUE KEY `uk_app_client_code` (`client_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='Desktop客户端';

-- ----------------------------
-- Table structure for app_client_release
-- ----------------------------

CREATE TABLE IF NOT EXISTS `app_client_release` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `client_id` bigint(20) unsigned NOT NULL COMMENT '客户端ID',
  `version_name` varchar(64) NOT NULL COMMENT '版本名称',
  `version_code` bigint(20) NOT NULL COMMENT '版本编码',
  `channel` varchar(32) NOT NULL DEFAULT 'stable' COMMENT '发布渠道',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
  `release_notes` text COMMENT '发布说明',
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
  UNIQUE KEY `uk_app_client_release_version` (`client_id`,`version_code`,`channel`),
  KEY `idx_app_client_release_query` (`client_id`,`channel`,`status`,`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='Desktop客户端版本';

-- ----------------------------
-- Table structure for app_client_release_artifact
-- ----------------------------

CREATE TABLE IF NOT EXISTS `app_client_release_artifact` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `release_id` bigint(20) unsigned NOT NULL COMMENT '版本发布ID',
  `platform` varchar(32) NOT NULL COMMENT '操作系统平台',
  `arch` varchar(32) NOT NULL COMMENT 'CPU架构',
  `file_id` varchar(64) NOT NULL COMMENT '安装包文件ID',
  `file_name` varchar(255) DEFAULT NULL COMMENT '安装包文件名',
  `size` bigint(20) DEFAULT NULL COMMENT '文件大小，单位字节',
  `sha256` varchar(64) NOT NULL COMMENT '文件SHA-256',
  `signature` text NOT NULL COMMENT 'Tauri更新签名',
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
  UNIQUE KEY `uk_app_client_release_artifact` (`release_id`,`platform`,`arch`),
  KEY `idx_app_client_release_artifact_release` (`release_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='Desktop客户端版本安装包';
