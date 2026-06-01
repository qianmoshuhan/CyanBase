-- ================================
-- CyanBase 青渊底座 - 阶段七 数据库建表脚本
-- 工作流引擎 + 自定义表单平台
-- 数据库：cyan_base @ 120.48.47.253:3306
-- ================================

-- ================================
-- 一、工作流模块表（5张）
-- ================================

-- 1. 流程定义表
CREATE TABLE IF NOT EXISTS `wf_process_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识（唯一）',
    `process_name` VARCHAR(200) NOT NULL COMMENT '流程名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '流程描述',
    `category` VARCHAR(50) DEFAULT NULL COMMENT '流程分类',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布 2-已停用',
    `start_node_id` BIGINT DEFAULT NULL COMMENT '起始节点ID',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_key_version` (`process_key`, `version`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表';

-- 2. 流程节点表
CREATE TABLE IF NOT EXISTS `wf_process_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `definition_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `node_key` VARCHAR(100) NOT NULL COMMENT '节点标识',
    `node_name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型：start-开始 end-结束 userTask-用户任务 serviceTask-服务任务 gateway-网关',
    `assignee_type` VARCHAR(50) DEFAULT NULL COMMENT '审批人类型：user-指定用户 role-指定角色 dept-指定部门',
    `assignee_value` VARCHAR(500) DEFAULT NULL COMMENT '审批人值（用户ID/角色ID/部门ID，多个用逗号分隔）',
    `approval_type` VARCHAR(50) DEFAULT NULL COMMENT '审批类型：or-或签 and-会签',
    `timeout_hours` INT DEFAULT NULL COMMENT '超时小时数',
    `next_node_ids` VARCHAR(500) DEFAULT NULL COMMENT '下一节点ID列表（多个用逗号分隔）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition_id` (`definition_id`),
    KEY `idx_node_key` (`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表';

-- 3. 流程实例表
CREATE TABLE IF NOT EXISTS `wf_process_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `definition_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识',
    `process_name` VARCHAR(200) NOT NULL COMMENT '流程名称',
    `business_key` VARCHAR(100) DEFAULT NULL COMMENT '业务关联键',
    `current_node_id` BIGINT DEFAULT NULL COMMENT '当前节点ID',
    `current_node_name` VARCHAR(200) DEFAULT NULL COMMENT '当前节点名称',
    `status` VARCHAR(50) NOT NULL DEFAULT 'running' COMMENT '状态：running-运行中 completed-已完成 cancelled-已取消 rejected-已驳回',
    `initiator_id` BIGINT NOT NULL COMMENT '发起人ID',
    `initiator_name` VARCHAR(100) DEFAULT NULL COMMENT '发起人姓名',
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
    `variables` TEXT COMMENT '流程变量（JSON格式）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition_id` (`definition_id`),
    KEY `idx_process_key` (`process_key`),
    KEY `idx_initiator_id` (`initiator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 4. 流程审批记录表
CREATE TABLE IF NOT EXISTS `wf_process_approve` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `node_id` BIGINT NOT NULL COMMENT '节点ID',
    `node_name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `approver_id` BIGINT NOT NULL COMMENT '审批人ID',
    `approver_name` VARCHAR(100) NOT NULL COMMENT '审批人姓名',
    `action` VARCHAR(50) NOT NULL COMMENT '操作：approve-通过 reject-驳回 transfer-转办',
    `comment` VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
    `approval_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '审批耗时（毫秒）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_instance_id` (`instance_id`),
    KEY `idx_approver_id` (`approver_id`),
    KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程审批记录表';

-- 5. 流程抄送表
CREATE TABLE IF NOT EXISTS `wf_process_copy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `user_id` BIGINT NOT NULL COMMENT '抄送用户ID',
    `user_name` VARCHAR(100) NOT NULL COMMENT '抄送用户姓名',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_instance_id` (`instance_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送表';

-- ================================
-- 二、自定义表单模块表（4张）
-- ================================

-- 1. 表单定义表
CREATE TABLE IF NOT EXISTS `form_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `form_key` VARCHAR(100) NOT NULL COMMENT '表单标识（唯一）',
    `form_name` VARCHAR(200) NOT NULL COMMENT '表单名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '表单描述',
    `category` VARCHAR(50) DEFAULT NULL COMMENT '表单分类',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布 2-已停用',
    `layout_config` TEXT COMMENT '布局配置（JSON格式）',
    `submit_rule` VARCHAR(500) DEFAULT NULL COMMENT '提交规则',
    `permission_config` TEXT COMMENT '权限配置（JSON格式）',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_key_version` (`form_key`, `version`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单定义表';

-- 2. 表单字段配置表
CREATE TABLE IF NOT EXISTS `form_field` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `definition_id` BIGINT NOT NULL COMMENT '表单定义ID',
    `field_key` VARCHAR(100) NOT NULL COMMENT '字段标识',
    `field_name` VARCHAR(200) NOT NULL COMMENT '字段名称',
    `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型：text-文本 textarea-多行文本 number-数字 date-日期 select-下拉 checkbox-多选 radio-单选 file-文件',
    `default_value` VARCHAR(500) DEFAULT NULL COMMENT '默认值',
    `placeholder` VARCHAR(200) DEFAULT NULL COMMENT '占位提示',
    `required` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：0-否 1-是',
    `readonly` TINYINT NOT NULL DEFAULT 0 COMMENT '是否只读：0-否 1-是',
    `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可见：0-否 1-是',
    `validation_rule` VARCHAR(500) DEFAULT NULL COMMENT '校验规则',
    `options` TEXT COMMENT '选项配置（JSON格式，用于下拉/多选/单选）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition_id` (`definition_id`),
    KEY `idx_field_key` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单字段配置表';

-- 3. 表单数据表
CREATE TABLE IF NOT EXISTS `form_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `definition_id` BIGINT NOT NULL COMMENT '表单定义ID',
    `form_key` VARCHAR(100) NOT NULL COMMENT '表单标识',
    `form_name` VARCHAR(200) NOT NULL COMMENT '表单名称',
    `business_key` VARCHAR(100) DEFAULT NULL COMMENT '业务关联键',
    `status` VARCHAR(50) NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿 submitted-已提交 approved-已通过 rejected-已驳回',
    `submitter_id` BIGINT NOT NULL COMMENT '提交人ID',
    `submitter_name` VARCHAR(100) NOT NULL COMMENT '提交人姓名',
    `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    `variables` TEXT COMMENT '表单变量（JSON格式）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition_id` (`definition_id`),
    KEY `idx_form_key` (`form_key`),
    KEY `idx_submitter_id` (`submitter_id`),
    KEY `idx_status` (`status`),
    KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单数据表';

-- 4. 表单数据项表（存储具体字段值）
CREATE TABLE IF NOT EXISTS `form_data_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_id` BIGINT NOT NULL COMMENT '表单数据ID',
    `field_key` VARCHAR(100) NOT NULL COMMENT '字段标识',
    `field_name` VARCHAR(200) NOT NULL COMMENT '字段名称',
    `field_value` TEXT COMMENT '字段值',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_data_id` (`data_id`),
    KEY `idx_field_key` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单数据项表';

-- ================================
-- 三、初始化数据（可选）
-- ================================

-- 插入示例流程定义
INSERT INTO `wf_process_definition` (`process_key`, `process_name`, `description`, `category`, `version`, `status`, `create_by`) 
VALUES ('leave_approval', '请假审批流程', '员工请假审批流程', '人事', 1, 1, 'admin');

-- 插入示例表单定义
INSERT INTO `form_definition` (`form_key`, `form_name`, `description`, `category`, `version`, `status`, `create_by`) 
VALUES ('leave_application', '请假申请表单', '员工请假申请', '人事', 1, 1, 'admin');

-- ================================
-- 完成
-- ================================