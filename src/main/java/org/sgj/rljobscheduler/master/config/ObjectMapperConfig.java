package org.sgj.rljobscheduler.master.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 如果需要缩进输出日志，可以取消注释
        // objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return objectMapper;
    }
}
