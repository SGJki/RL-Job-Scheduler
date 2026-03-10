package org.sgj.rljobscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;


@SpringBootApplication
@EnableAsync
@MapperScan("org.sgj.rljobscheduler.mapper")
public class RlJobSchedulerApplication {

    public static void main(String[] args) {

        SpringApplication.run(RlJobSchedulerApplication.class, args);
        System.out.println("Rl Job Scheduler Application started in http://localhost:8080/");
    }

}
