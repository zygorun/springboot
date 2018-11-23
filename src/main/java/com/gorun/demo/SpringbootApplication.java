package com.gorun.demo;

import com.gorun.demo.base.BaseController;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//开启定时任务
//@EnableScheduling
//开启缓存
@EnableCaching
@MapperScan("com.gorun.demo.mapper")
public class SpringbootApplication extends BaseController {


    public static void main(String[] args) {
        SpringApplication.run(SpringbootApplication.class, args);
    }
}
