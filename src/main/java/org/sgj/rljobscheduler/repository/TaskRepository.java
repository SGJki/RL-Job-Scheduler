package org.sgj.rljobscheduler.repository;
import org.sgj.rljobscheduler.entity.TrainingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 仓库层 (Repository / DAO)
 * 负责跟数据库打交道
 *
 * 只需要继承 JpaRepository<实体类, 主键类型>
 * Spring Data JPA 会自动帮你实现: save(), findById(), findAll(), delete() 等方法
 * 不需要写一行 SQL！
 */
@Repository
public interface TaskRepository extends JpaRepository<TrainingTask, String> {

    // 你还可以自定义查询方法，比如根据状态查询
    List<TrainingTask> findByid(String status);
}
