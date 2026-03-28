package org.sgj.rljobscheduler.master;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableDiscoveryClient
@MapperScan("org.sgj.rljobscheduler.master.mapper")
public class RlJobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RlJobSchedulerApplication.class, args);
    }

}
