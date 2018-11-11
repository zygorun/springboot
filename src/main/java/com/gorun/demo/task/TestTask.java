package com.gorun.demo.task;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class TestTask {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "HH:mm:ss");

    @Scheduled(fixedDelay = 3000)
    public void taskReport() {
        System.out.println("现在时间为：" + dateFormat.format(new Date()));
    }
}
