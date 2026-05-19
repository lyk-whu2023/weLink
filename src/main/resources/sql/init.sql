-- WeLink Database Initialization Script

CREATE DATABASE IF NOT EXISTS welink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE welink;

-- User Table
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) UNIQUE NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `nickname` VARCHAR(50),
  `avatar` VARCHAR(255),
  `email` VARCHAR(100),
  `phone` VARCHAR(20),
  `status` TINYINT DEFAULT 1 COMMENT '1-正常 0-禁用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_username` (`username`),
  INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- Friend Relation Table
CREATE TABLE IF NOT EXISTS `friend_relation` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `friend_id` BIGINT NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0-待确认 1-已好友 2-已拒绝',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- Group Info Table
CREATE TABLE IF NOT EXISTS `group_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_name` VARCHAR(100) NOT NULL,
  `avatar` VARCHAR(255),
  `owner_id` BIGINT NOT NULL,
  `notice` TEXT COMMENT '群公告',
  `member_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '1-正常 0-解散',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组表';

-- Group Member Table
CREATE TABLE IF NOT EXISTS `group_member` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` TINYINT DEFAULT 0 COMMENT '0-普通成员 1-管理员 2-群主',
  `last_read_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '群已读游标',
  `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  INDEX `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';

-- Message Table
CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `msg_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '消息唯一ID',
  `from_user_id` BIGINT NOT NULL,
  `to_user_id` BIGINT COMMENT '目标用户ID(私聊)',
  `group_id` BIGINT COMMENT '群组ID(群聊)',
  `group_seq` BIGINT DEFAULT NULL COMMENT '群聊单调递增序号',
  `msg_type` TINYINT NOT NULL COMMENT '1-文本 2-图片 3-文件 4-系统',
  `content` TEXT NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_from_user` (`from_user_id`),
  INDEX `idx_to_user` (`to_user_id`),
  INDEX `idx_group_id` (`group_id`),
  INDEX `idx_group_seq` (`group_id`, `group_seq`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- Message Inbox Table
CREATE TABLE IF NOT EXISTS `message_inbox` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `msg_id` VARCHAR(64) NOT NULL COMMENT '消息唯一ID',
  `receiver_id` BIGINT NOT NULL COMMENT '接收者ID',
  `conversation_type` TINYINT NOT NULL COMMENT '1-私聊 2-群聊',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-SENT 1-DELIVERED 2-READ',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_msg_receiver` (`msg_id`, `receiver_id`),
  INDEX `idx_receiver_status_created` (`receiver_id`, `status`, `created_at`),
  INDEX `idx_msg_id` (`msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息收件箱表';

-- Message Outbox Table
CREATE TABLE IF NOT EXISTS `message_outbox` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `msg_id` VARCHAR(64) NOT NULL COMMENT '消息唯一ID',
  `target_user_id` BIGINT NOT NULL COMMENT '目标用户ID',
  `topic` VARCHAR(64) NOT NULL COMMENT 'Kafka主题',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-PENDING 1-PUBLISHED 2-FAILED 3-PUBLISHING',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
  `last_error` VARCHAR(512) DEFAULT NULL COMMENT '最近错误',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_msg_target_topic` (`msg_id`, `target_user_id`, `topic`),
  INDEX `idx_status_next_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息Outbox表';
