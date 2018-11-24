package com.gorun.demo.controller;

import com.gorun.demo.base.BaseController;
import com.gorun.demo.base.HttpResultModel;
import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {
    @Autowired
    UserService userService;

    @RequestMapping(value = "/select")
    public HttpResultModel select() {
        return sendResult(userService.allData(), null);
    }

    @RequestMapping(value = "/insertRollBack", produces = {"application/json;" +
            "charset=UTF-8"})
    @Transactional(rollbackFor = {IllegalArgumentException.class})//表示数据存在回滚
    public int insertRollBack(User user) {
        int res = userService.insertData(user);
        if (user.getName().equals("gorun")) {
            throw new IllegalArgumentException("该条记录已存在，事务将回滚");
        }
        return res;
    }

    @RequestMapping(value = "/insertWithoutRollBack", produces = {"application/json;" +
            "charset=UTF-8"})
    @Transactional(noRollbackFor = {IllegalArgumentException.class})//表示数据存在不回滚
    public int insertWithoutRollBack(User user) {
        int res = userService.insertData(user);
        if (user.getName().equals("gorun")) {
            throw new IllegalArgumentException("该条记录已存在，事务将回滚");
        }
        return res;
    }

    @RequestMapping(value = "/insert", produces = {"application/json;" +
            "charset=UTF-8"})
    public HttpResultModel insert(User user) {
        return sendResult(userService.insertData(user), null);
    }

    @RequestMapping(value = "/selectOneInfo")
    public HttpResultModel selectOneInfo(int id) {
        return sendResult(userService.selectOneInfo(id), null);
    }

    @RequestMapping("/updateInfo")
    public HttpResultModel updateInfo(User user) {
        return sendResult(userService.updateInfo(user), null);
    }
}
