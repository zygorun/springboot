package com.gorun.demo;

import com.gorun.demo.service.AuthorSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class Hello {
    @Autowired
    private AuthorSetting authorSetting;

    @RequestMapping("/index")
    public String index() {
        return "Hello Spring Boot";
    }

    @RequestMapping("/info")
    public String info() {
        return "name: " + authorSetting.getName();
    }
}
