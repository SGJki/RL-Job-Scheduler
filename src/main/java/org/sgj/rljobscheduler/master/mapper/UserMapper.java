package org.sgj.rljobscheduler.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.sgj.rljobscheduler.master.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User>{
}
