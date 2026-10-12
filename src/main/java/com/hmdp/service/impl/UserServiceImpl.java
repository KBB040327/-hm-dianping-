package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

//    发送短信验证
    @Override
    public Result sendCode(String phone, HttpSession session) {
//        校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
//        如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
//        生成验证码
        String code = RandomUtil.randomNumbers(6);
//        保存验证码到session
//        session.setAttribute("code",code);
//        保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        发送验证码
        log.debug("短信发送成功,验证码为:{}",code);
//        返回ok
        return Result.ok();
    }

//    登录功能实现
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
//        如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
//        校验验证码
//        从redis获取验证码并校验
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        Object cachecode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.equals(code)) {
//        不一致，报错
            return Result.fail("验证码错误");
        }
//        一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
//        判断用户是否存在
        if (user == null){
//        不存在：创建新用户并保存到数据库
            user = createUserWithPhone(phone);
        }
//        存在保存到session中
//        保存用户到redis中
//        随即生成token作为登录令牌
        String token = UUID.randomUUID().toString();
//        将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue.toString()));
//        存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        设置token的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        返回token
        return Result.ok(token);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

    }
/**
 * 签到功能
 */
    @Override
    public Result sign() {
//        获取当前用户Id
        Long userId = UserHolder.getUser().getId();
//        获取日期
        LocalDateTime now = LocalDateTime.now();
//        拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
//        获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
//        写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }
/**
 * 统计签到功能
 */
    @Override
    public Result signCount() {
        //        获取当前用户Id
        Long userId = UserHolder.getUser().getId();
//        获取日期
        LocalDateTime now = LocalDateTime.now();
//        拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
//        获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
//        获取本月截止今天的所有签到记录，返回一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }
//        循环遍历
        int count = 0;
        while (true) {
//        让这个数字和1做与运算，得到数字最后的最后一个bit位  //        判断bit是否为0
            if ((num & 1) == 0) {
                //        如果为0，未签到，结束
                break;
            }else {
//        如果为1，已签到，计数器加1
                count++;
            }
//        把数字右移一位，继续下一位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
//        创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
//        保存用户
        save(user);
        return user;
    }
}
