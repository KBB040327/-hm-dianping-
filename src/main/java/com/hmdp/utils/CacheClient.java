package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time,
                                          TimeUnit unit){
        String key = keyPrefix + id;
//        从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        判断缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            //        命中，返回商铺信息
            return JSONUtil.toBean(json,  type);
        }
//        判断命中，空值返回错误
        if (json != null){
            return null;
        }
//        未命中，根据id查询数据库
        R r = dbFallback.apply(id);
//        判读商铺是否存在
        if (r == null){
            //        不存在，返回错误
//            将空值写入redis中
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        存在，将商铺写入redis中
        this.set(key, r, time, unit);
//        返回结果
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                            ID id, Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit){
        String key = keyPrefix + id;
//        从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        判断缓存是否命中
        if (StrUtil.isBlank(json)) {
            return null;
        }
//        命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
//        判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
//        未过期直接返回商铺信息
            return r;
        }
//        过期，需要缓存重建
//        获取互斥锁
        String lockkey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockkey);
//        判断是否获取成功
        if (isLock){
            //        成功开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                重建缓存
                try {
//                    查询数据库
                    R r1 = dbFallback.apply(id);
//                    写入redis
                    this.set(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //                释放锁
                    unLock(lockkey);
                }
            });
        }

//        返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //    释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
