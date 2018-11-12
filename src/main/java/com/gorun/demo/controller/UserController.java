package com.gorun.demo.controller;

import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional(rollbackFor = {IllegalArgumentException.class})
    public int insert(User user) {
        int res = userService.insertData(user);
        if (user.getName().equals("gorun")) {
            throw new IllegalArgumentException("该条记录已存在，事物将回滚");
        }
        return res;
    }
}
