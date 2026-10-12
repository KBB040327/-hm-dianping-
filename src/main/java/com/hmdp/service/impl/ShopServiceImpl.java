package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
//        缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,
//                id, Shop.class,
//                this::getById,
//                CACHE_SHOP_TTL,
//                TimeUnit.MINUTES);
//        互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //        逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,
                id, Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
//        返回结果
        return Result.ok(shop);
    }

private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
////        从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
////        判断缓存是否命中
//        if (StrUtil.isBlank(shopJson)) {
//            return null;
//        }
////        命中，把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
////        判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
////        未过期直接返回商铺信息
//            return shop;
//        }
////        过期，需要缓存重建
////        获取互斥锁
//        String lockkey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockkey);
////        判断是否获取成功
//        if (isLock){
//            //        成功开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
////                重建缓存
//                try {
//                    this.saveShop2Redis(id, 20L);
//
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    //                释放锁
//                    unLock(lockkey);
//                }
//            });
//        }
//
////        返回过期的商铺信息
//        return shop;
//    }




////        获取锁
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
////    释放锁
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }

//    public Shop queryWithMutex (Long id){
//        String key = CACHE_SHOP_KEY + id;
////        从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
////        判断缓存是否命中
//        if (StrUtil.isNotBlank(shopJson)) {
//            //        命中，返回商铺信息
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
////        判断命中，空值返回错误
//        if (shopJson != null){
//            return null;
//        }
//
////        实现缓存重建
////        获取互斥锁
//        String lockkey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockkey);
////        判断是否获取成功
//            if (!isLock){
//                //        失败休眠重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
////        成功，根据id查询数据库
//            shop = getById(id);
////            模拟耗时延迟
//            Thread.sleep(200);
////        判读商铺是否存在
//            if (shop == null){
//                //        不存在，返回错误
//    //            将空值写入redis中
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
////        存在，将商铺写入redis中
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //        释放互斥锁
//            unLock(lockkey);
//        }
////        返回结果
//        return shop;
//    }

//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
////        从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
////        判断缓存是否命中
//        if (StrUtil.isNotBlank(shopJson)) {
//            //        命中，返回商铺信息
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
////        判断命中，空值返回错误
//        if (shopJson != null){
//            return null;
//        }
////        未命中，根据id查询数据库
//        Shop shop = getById(id);
////        判读商铺是否存在
//        if (shop == null){
//            //        不存在，返回错误
////            将空值写入redis中
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
////        存在，将商铺写入redis中
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
////        返回结果
//        return shop;
//    }


//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
////        查询店铺数据
//        Shop shop = getById(id);
//        Thread.sleep(200);
////        封装逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
////        写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
//        更新数据库
        updateById(shop);
//        删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
//        放回结果
        return Result.ok();
    }
/**
 * 根据类型分页查询商铺信息
 * @param typeId 商铺类型
 * @param current 页码
 * @return 商铺列表
 */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
//        判断是否需要根据坐标查询
        if (x == null || y == null) {
//            不需要坐标查询，按照数据库查询
                    Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
        return Result.ok(page.getRecords());
        }
//        计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
//        查询redis，按照距离排序，分页查询
        String key = SHOP_GEO_KEY + typeId;
        // 1. 构造查询中心点坐标
        Point center = new Point(x, y);
        // 2. 半径5000米，指定单位为米（Metrics.METERS）
        Distance radius = new Distance(5000, RedisGeoCommands.DistanceUnit.METERS);
        // 3. 配置查询参数：返回距离、限制结果条数
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()  // 返回每个点位到中心的距离
                .limit(end);        // 限制返回数量，实现分页截断
// 执行Redis5兼容的GEORADIUS查询API
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, new org.springframework.data.geo.Circle(center, radius), args);
//        解析出Id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
//        截取从from到end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
//        根据Id查询店铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
//        返回
        return Result.ok(shops);
    }
}
