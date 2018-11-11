package com.gorun.demo.controller;

import com.gorun.demo.mapper.UserMapper;
import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    UserService userService;

    @RequestMapping(value = "/select", produces = {"application/json;" +
            "charset=UTF-8"})
    public List<User> select() {
        return userService.allData();
    }

    @RequestMapping(value = "/insert", produces = {"application/json;" +
            "charset=UTF-8"})
    public int insert(User user) {
        return userService.insertData(user);
    }
}
