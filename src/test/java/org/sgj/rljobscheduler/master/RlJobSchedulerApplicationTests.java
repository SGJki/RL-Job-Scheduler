package org.sgj.rljobscheduler.master;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = RlJobSchedulerApplication.class,
        properties = {
                "spring.sql.init.mode=never",
                "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driverClassName=org.h2.Driver"
        }
)
class RlJobSchedulerApplicationTests {

    @Test
    void contextLoads() {
    }
}
