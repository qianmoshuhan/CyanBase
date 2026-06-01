-- ================================
-- CyanBase 青渊底座 - 阶段六 SQL脚本
-- 功能：定时任务 + 站内消息通知 + 系统自动化任务
-- 执行前请备份数据库
-- ================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 优化 sys_job 定时任务表（已存在，补充字段）
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调用目标字符串(beanName.methodName)',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` tinyint(4) NULL DEFAULT 1 COMMENT '执行策略(1立即执行 2触发一次 3放弃执行)',
  `concurrent` tinyint(4) NULL DEFAULT 0 COMMENT '是否并发执行(0允许 1禁止)',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '状态(0正常 1暂停)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建人',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新人',
  `create_time` datetime NULL DEFAULT current_timestamp() COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint(4) NULL DEFAULT 0 COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_job_name`(`job_name`) USING BTREE,
  INDEX `idx_job_group`(`job_group`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '定时任务调度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 2. 优化 sys_job_log 定时任务日志表（已存在，补充字段）
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`  (
  `job_log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_id` bigint(20) NOT NULL COMMENT '任务ID',
  `job_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '日志信息',
  `exception_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '异常信息',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '执行状态(0成功 1失败)',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `cost_time` bigint(20) NULL DEFAULT 0 COMMENT '耗时(毫秒)',
  `create_time` datetime NULL DEFAULT current_timestamp() COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE,
  INDEX `idx_job_id`(`job_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '定时任务调度日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 3. 优化 sys_message 消息表（已存在，重构设计）
-- ----------------------------
DROP TABLE IF EXISTS `sys_message`;
CREATE TABLE `sys_message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `tenant_id` bigint(20) NULL DEFAULT 0 COMMENT '集团ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '消息内容',
  `message_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '消息类型(1系统公告 2操作通知 3预警提醒 4欢迎消息)',
  `send_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '发送方式(1全员 2指定用户 3指定角色)',
  `sender_id` bigint(20) NULL DEFAULT 0 COMMENT '发送人ID(0表示系统)',
  `sender_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '系统' COMMENT '发送人姓名',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '状态(0正常 1关闭)',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NULL DEFAULT current_timestamp() COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint(4) NULL DEFAULT 0 COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_type`(`message_type`) USING BTREE,
  INDEX `idx_send_type`(`send_type`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息通知表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 4. 新增 sys_message_user 用户消息关联表（支持已读状态）
-- ----------------------------
DROP TABLE IF EXISTS `sys_message_user`;
CREATE TABLE `sys_message_user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `message_id` bigint(20) NOT NULL COMMENT '消息ID',
  `user_id` bigint(20) NOT NULL COMMENT '接收用户ID',
  `is_read` tinyint(4) NULL DEFAULT 0 COMMENT '是否已读(0未读 1已读)',
  `read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NULL DEFAULT current_timestamp() COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint(4) NULL DEFAULT 0 COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_user`(`message_id`, `user_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_is_read`(`is_read`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户消息关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 5. 初始化定时任务数据
-- ----------------------------
INSERT INTO `sys_job` VALUES 
(1, '清理7天前操作日志', 'SYSTEM', 'sysJobTask.cleanOperationLog', '0 0 2 * * ?', 1, 1, 0, '每天凌晨2点清理7天前的操作日志', '', '', NOW(), NOW(), 0),
(2, '清理7天前登录日志', 'SYSTEM', 'sysJobTask.cleanLoginLog', '0 0 2 * * ?', 1, 1, 0, '每天凌晨2点清理7天前的登录日志', '', '', NOW(), NOW(), 0),
(3, '清理30天前临时文件', 'SYSTEM', 'sysJobTask.cleanTempFile', '0 0 3 * * ?', 1, 1, 0, '每天凌晨3点清理30天前的临时文件', '', '', NOW(), NOW(), 0),
(4, '清理过期Redis缓存', 'SYSTEM', 'sysJobTask.cleanExpiredCache', '0 0 4 * * ?', 1, 1, 0, '每天凌晨4点清理过期的Redis缓存', '', '', NOW(), NOW(), 0),
(5, '每日用户登录统计', 'SYSTEM', 'sysJobTask.dailyLoginStatistics', '0 0 0 * * ?', 1, 1, 0, '每天凌晨0点统计昨日用户登录数据', '', '', NOW(), NOW(), 0),
(6, '同步权限数据到Redis', 'SYSTEM', 'sysJobTask.syncPermissionToRedis', '0 */30 * * * ?', 1, 1, 0, '每30分钟同步一次权限数据到Redis', '', '', NOW(), NOW(), 0);

-- ----------------------------
-- 6. 初始化消息类型字典数据
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES 
(10, '消息类型', 'sys_message_type', 0, '', '', '', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE dict_type_name='消息类型';

INSERT INTO `sys_dict_data` VALUES 
(20, 'sys_message_type', '系统公告', '1', 1, 0, '', '', '', NOW(), NOW(), 0),
(21, 'sys_message_type', '操作通知', '2', 2, 0, '', '', '', NOW(), NOW(), 0),
(22, 'sys_message_type', '预警提醒', '3', 3, 0, '', '', '', NOW(), NOW(), 0),
(23, 'sys_message_type', '欢迎消息', '4', 4, 0, '', '', '', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label);

SET FOREIGN_KEY_CHECKS = 1;
