package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.*;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) throws InterruptedException {

//        //解决缓存穿透
//        Shop shop = cacheClient.
//                queryWithPassThrough(
//                        CACHE_SHOP_KEY,
//                        id,
//                        Shop.class,
//                        this::getById,  // 等同于 sid-> getById(sid)
//                        CACHE_COMMON_TTL,
//                        TimeUnit.SECONDS
//                );

        //缓存击穿
//        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY,
//                id,
//                Shop.class,
//                this::getById,  // 等同于 sid-> getById(sid)
//                CACHE_COMMON_TTL,
//                TimeUnit.SECONDS,
//                LOCK_SHOP_KEY);

        //缓存击穿
                Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,  // 等同于 sid-> getById(sid)
                CACHE_COMMON_TTL,
                TimeUnit.SECONDS,
                LOCK_SHOP_KEY);
        if (shop == null) {
            return Result.fail("店铺路不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result myUpdate(Shop shop) {
        //1.更新数据库
        Long id = shop.getId();
        updateById(shop);
        if(id == null){
            return Result.fail("用户id不能为空");
        }
        //2.更新缓存

        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);

        return Result.ok();
    }
}
