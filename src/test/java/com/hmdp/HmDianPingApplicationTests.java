package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdworker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdworker redisIdworker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 线程池
     */
    private ExecutorService executorService = Executors.newFixedThreadPool(500);


    @Test
    public void testIdWorker() throws InterruptedException {
        /**
         * 创建线程
         */
        CountDownLatch countDownLatch = new CountDownLatch(30);
        Runnable task = () -> {
            for (int i = 0; i < 10; i++) {
                long id = redisIdworker.nextId("order");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }


    @Test
    public void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void loadShopData(){
//        查询店铺id
        List<Shop> list = shopService.list();
//        根据typeId进行分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//        存入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
//            获取类型Id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
//            获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
//            写入redis
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add
                        (new RedisGeoCommands.GeoLocation<>
                                (shop.getId().toString(),
                                        new Point(shop.getX(),
                                                shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        int total = 1000000;
        for (int i = 0; i < total; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 若总数不是1000整数倍，把剩余未提交的数据写入
        int remainder = total % 1000;
        if (remainder != 0) {
            String[] last = new String[remainder];
            System.arraycopy(values, 0, last, 0, remainder);
            stringRedisTemplate.opsForHyperLogLog().add("hl2", last);
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }
}
