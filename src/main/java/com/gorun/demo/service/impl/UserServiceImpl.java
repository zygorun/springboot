package com.gorun.demo.service.impl;

import com.gorun.demo.mapper.UserMapper;
import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    public List<User> allData() {
        return userMapper.selectByExample(null);
    }

    @Override
    @CachePut(value = "putCache", key = "#id")
    public int insertData(User user) {
        return userMapper.insertSelective(user);
    }

    @Override
    @Cacheable(value = "selectCache", key = "#id")
    public User selectOneInfo(int id) {
        return userMapper.selectByPrimaryKey(id);
    }
}
