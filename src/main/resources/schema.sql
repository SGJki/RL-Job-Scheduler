CREATE TABLE IF NOT EXISTS training_task (
                                             id VARCHAR(50) NOT NULL COMMENT '主键ID',
                                             algorithm VARCHAR(100) COMMENT '算法名称',
                                             episodes INT COMMENT '训练回合数',
                                             learning_rate DOUBLE COMMENT '学习率',
                                             status VARCHAR(50) COMMENT '任务状态',
                                             final_reward DOUBLE COMMENT '最终奖励',
                                             created_at DATETIME COMMENT '创建时间',
                                             completed_at DATETIME COMMENT '完成时间',
                                             PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;