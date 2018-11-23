package com.gorun.demo.task;

import cn.hutool.core.lang.Console;
import com.gorun.demo.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class Consumer {
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consumeMessage(String message) {
        Console.log("consume message {}", message);
    }
}
