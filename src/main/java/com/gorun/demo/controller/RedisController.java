package com.gorun.demo.controller;

import com.gorun.demo.base.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redis")
public class RedisController extends BaseController {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @RequestMapping("/value")
    public String redis() {
        return stringRedisTemplate.opsForValue().get("address");
    }
}
