package com.gorun.demo.service;

import com.gorun.demo.model.User;

import java.util.List;

public interface UserService {
    List<User> allData();

    int insertData(User user);

    User selectOneInfo(int id);

    int updateInfo(User user);
}
