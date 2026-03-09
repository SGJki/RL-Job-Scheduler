package org.sgj.rljobscheduler.controller;

import org.sgj.rljobscheduler.dto.TrainingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
public class TestController {
    @Autowired
    private TrainingController trainingController;

    @org.junit.jupiter.api.Test
    public void testTrainWithNullParams() {
        System.out.println(">>> 开始测试: 传入 null 参数");

        // 构造一个只传了 algorithm，其他为 null 的请求
        TrainingRequest request = new TrainingRequest();
        request.setAlgorithm("PPO");
//        request.setEpisodes(null);   // 显式设置为 null
//        request.setLearningRate(null); // 显式设置为 null

        // 验证调用 controller 不会抛出异常
        assertDoesNotThrow(() -> {
            trainingController.train(request);
        });

        System.out.println(">>> 测试通过: Controller 成功处理了 null 参数");
    }
}


