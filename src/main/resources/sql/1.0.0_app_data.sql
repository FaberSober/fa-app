SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Records of base_rbac_menu
-- ----------------------------
BEGIN;
INSERT INTO `base_rbac_menu` (`id`, `parent_id`, `name`, `sort`, `level`, `icon`, `status`, `link_type`, `link_url`, `crt_time`, `crt_user`, `crt_name`, `crt_host`, `upd_time`, `upd_user`, `upd_name`, `upd_host`, `deleted`) VALUES (200000, 0, 'APP管理', 3, 0, 'mobile-retro', 1, 1, '/admin/app', '2023-01-20 14:22:45', '1', '超级管理员', '127.0.0.1', NULL, NULL, NULL, NULL, 0);

INSERT INTO `base_rbac_menu` (`id`, `parent_id`, `name`, `sort`, `level`, `icon`, `status`, `link_type`, `link_url`, `crt_time`, `crt_user`, `crt_name`, `crt_host`, `upd_time`, `upd_user`, `upd_name`, `upd_host`, `deleted`) VALUES (210000, 200000, 'APP版本管理', 0, 1, 'store', 1, 1, '/admin/app/app/apk', '2023-01-20 14:23:14', '1', '超级管理员', '127.0.0.1', NULL, NULL, NULL, NULL, 0);
INSERT INTO `base_rbac_menu` (`id`, `parent_id`, `name`, `sort`, `level`, `icon`, `status`, `link_type`, `link_url`, `crt_time`, `crt_user`, `crt_name`, `crt_host`, `upd_time`, `upd_user`, `upd_name`, `upd_host`, `deleted`) VALUES (220000, 200000, 'APP崩溃日志', 1, 1, 'burst', 1, 1, '/admin/app/crash/apkCrash', '2023-01-20 14:23:29', '1', '超级管理员', '127.0.0.1', NULL, NULL, NULL, NULL, 0);
COMMIT;


SET FOREIGN_KEY_CHECKS = 1;
