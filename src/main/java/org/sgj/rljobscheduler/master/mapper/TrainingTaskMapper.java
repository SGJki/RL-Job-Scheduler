package org.sgj.rljobscheduler.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper 接口
 * 只需要继承 BaseMapper<实体类>，就自动拥有了:
 * insert, deleteById, updateById, selectById, selectList 等方法
 * @Mapper 注解必须加！
 */
@Mapper
public interface TrainingTaskMapper extends BaseMapper<TrainingTask> {
    // 这里可以写自定义的 SQL 方法，比如:
    // @Select("SELECT * FROM training_task WHERE status = #{status}")
    // List<TrainingTask> selectByStatus(String status);
}