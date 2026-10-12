package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdworker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdworker redisIdworker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderTask());
    }
    private class VoucherOrderTask implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
//                    判断消息获取成功
                    if (list == null || list.isEmpty()) {
                        //                    获取失败，循环获取
                        continue;
                    }
//                    解析消息中的订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    获取成功，下单
                    handleVoucherOrder(voucherOrder);
//                    ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }
    private void handlePendingList() {
        while (true) {
            try {
                // 获取pending-list中的订单信息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
//                    判断消息获取成功
                if (list == null || list.isEmpty()) {
                    //                    获取失败,说明队列中没有数据了，跳出循环
                    break;
                }
//                    解析消息中的订单
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    获取成功，下单
                handleVoucherOrder(voucherOrder);
//                    ACK确认
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
            } catch (Exception e) {
                log.error("处理订单异常", e);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
        }
    }

//    private class VoucherOrderTask implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        获取用户id
        Long userId = voucherOrder.getUserId();
        //        创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
//            释放锁
            lock.unlock();
        }
    }

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
//    @Override
//    public Result setkillVoucher(Long voucherId) {
////        查询优惠卷信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//        //        判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 加锁控制一人一单，使用字符串常量池避免锁失效
////        创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
////        获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("请勿重复下单");
//        }
//        try {
//            // 获取代理对象调用事务方法，避免this调用导致事务失效
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
////            释放锁
//            lock.unlock();
//        }
//    }
    private IVoucherOrderService proxy;

    @Override
    public Result setkillVoucher(Long voucherId) {
//        获取用户id
        Long userId = UserHolder.getUser().getId();
        // 生成订单id
        long orderId = redisIdworker.nextId("order");
//        1、执行Lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),
                String.valueOf(orderId));
//        2、判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //        2.1、不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "请勿重复下单");
        }
        // 获取代理对象调用事务方法，避免this调用导致事务失效
        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        3、返回订单id
        return Result.ok(orderId);
    }


//    @Override
//    public Result setkillVoucher(Long voucherId) {
////        获取用户id
//        Long userId = UserHolder.getUser().getId();
////        1、执行Lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
////        2、判断结果是否为0
//        int r = result.intValue();
//        if (r != 0) {
//            //        2.1、不为0，代表没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "请勿重复下单");
//        }
////        2.2、为0，有购买资格，把下单信息保存到阻塞队列中
//        long orderId = redisIdworker.nextId("order");
//        //        创建订单
//        VoucherOrder order = new VoucherOrder();
////        订单id
//        order.setId(orderId);
////        用户id
//        order.setUserId(userId);
////        优惠卷id
//        order.setVoucherId(voucherId);
////        放入阻塞队列中
//        orderTasks.add(order);
//        // 获取代理对象调用事务方法，避免this调用导致事务失效
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
////        3、返回订单id
//        return Result.ok(orderId);
//    }

    @Transactional
    public void createVoucherOrder (VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
//        一人一单
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                log.error("请勿重复下单");
                return;
            }
//        充足扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0)
                    .update();
            if (!success) {
                log.error("库存不足");
                return;
            }

//        创建订单
//            VoucherOrder order = new VoucherOrder();
////        订单id
//            long orderId = redisIdworker.nextId("order");
//            order.setId(orderId);
////        用户id
//            order.setUserId(userId);
////        优惠卷id
//            order.setVoucherId(voucherOrder);
            save(voucherOrder);
//        返回结果
//            return Result.ok(orderId);
        }
}
