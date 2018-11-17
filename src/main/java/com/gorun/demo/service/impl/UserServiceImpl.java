package com.gorun.demo.service.impl;

import com.gorun.demo.mapper.UserMapper;
import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import net.sf.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    String result;

    public List<User> allData() {
        if (this.result == null) {
            stringRedisTemplate.opsForValue().set("user",
                    JSONArray.fromObject(userMapper.selectByExample(null)).toString());
        }
        this.result = stringRedisTemplate.opsForValue().get("user");
        return JSONArray.fromObject(this.result);
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
