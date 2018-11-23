package com.gorun.demo.service.impl;

import com.gorun.demo.mapper.UserMapper;
import com.gorun.demo.model.User;
import com.gorun.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    public List<User> allData() {
        return userMapper.selectByExample(null);
    }

    @Override
    public int insertData(User user) {
        return userMapper.insertSelective(user);
    }

    @Override
    public User selectOneInfo(int id) {
        String key = "user_" + id;
        ValueOperations<String, User> operations = redisTemplate.opsForValue();
        boolean hasKey = redisTemplate.hasKey(key);
        if (hasKey) {
            User user = operations.get(key);
            return user;
        } else {
            User user = userMapper.selectByPrimaryKey(id);
            operations.set(key, user, 5, TimeUnit.MINUTES);
            return user;
        }

    }

    @Override
    public int updateInfo(User user) {
        int result = userMapper.updateByPrimaryKeySelective(user);
        if (result > 0) {
            String key = "user_" + user.getId();
            boolean hasKey = redisTemplate.hasKey(key);
            if (hasKey) {
                redisTemplate.delete(key);
            }
        }
        return result;
    }
}
