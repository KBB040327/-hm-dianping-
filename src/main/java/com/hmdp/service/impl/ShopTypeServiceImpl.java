package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
//        从redis中查询商铺类型缓存
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(key, 0, -1);
//        存在直接返回
        if (CollectionUtil.isNotEmpty(shopTypeJson)) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson.toString(), ShopType.class);
            Collections.sort(shopTypes, (o1, o2) -> o1.getSort() - o2.getSort());
            return Result.ok(shopTypes);
        }
//        不存在查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
//        不存在返回错误
        if (CollectionUtil.isEmpty(shopTypes)) {
            return Result.fail("商铺类型不存在...");
        }
//        存在写入redis
        List<String> shopTypesJson = shopTypes.stream()
                .map(shopType -> JSONUtil.toJsonStr(shopType))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypesJson);
//        返回结果
        return Result.ok(shopTypes);
    }
}
