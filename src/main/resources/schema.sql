CREATE TABLE IF NOT EXISTS `training_task` (
    `id` VARCHAR(50) NOT NULL COMMENT '主键ID',
    `algorithm` VARCHAR(100) COMMENT '算法名称',
    `episodes` INT COMMENT '训练回合数',
    `learning_rate` DOUBLE COMMENT '学习率',
    `status` VARCHAR(50) COMMENT '任务状态',
    `final_reward` DOUBLE COMMENT '最终奖励',
    `created_at` DATETIME COMMENT '创建时间',
    `completed_at` DATETIME COMMENT '完成时间',
    `user_id` BIGINT COMMENT '用户ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `role` VARCHAR(20) NOT NULL COMMENT 'ADMIN/USER',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;