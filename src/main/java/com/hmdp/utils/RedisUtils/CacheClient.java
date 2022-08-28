package com.hmdp.utils.RedisUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.SystemConstants.*;

/**
 * @author L.J.L
 * @QQ 963314043
 * @date 2022/8/27 17:04
 */

@Component
public class CacheClient {
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_COMMON_TTL, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }



    public void set(String key , Object value, Long time , TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key , Object value, Long time , TimeUnit unit){

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }



    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallback,
                                         Long time ,
                                         TimeUnit unit){

        String key = keyPrefix + id;
        //1.从redis查
        String json = stringRedisTemplate.opsForValue().get(key);



        //2.存在返回
        if (StrUtil.isNotBlank(json)){
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        //命中空值
        if ("".equals(json)) {
            return null;
        }


        //3.不存在查数据库
        R r = dbFallback.apply(id);

        if(r ==null){
            //当数据库没有这个时候把空值存进redis  以防每次都查询数据库
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        this.set(key,r,time,unit);
        return r;
    }




    public <R,ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type,
                                     Function<ID,R> dbFallback,
                                     Long time ,
                                     TimeUnit unit, String lockKeyPrefix)  {

        String key = keyPrefix + id;
        //1.从redis查
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.若果不存在  我自己预热 视频里直接返回null了
        if (StrUtil.isBlank(json)){
            R r1 = dbFallback.apply(id);
            this.setWithLogicExpire(key,r1,time,unit);
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r2 = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();


        //判断时间是否过期
        //未过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return r2;
        }

        //已过期  缓存重建
        String lockKey = lockKeyPrefix +id;
        boolean isLock = tryLock(lockKey);


        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {

                    R r3 = dbFallback.apply(id);
                    this.setWithLogicExpire(key,r3,time,unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
                //释放锁

            });
        }



        return r2;
    }



    //互斥锁解决缓存击穿
    public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                        Function<ID,R> dbFallback,
                                        Long time ,
                                        TimeUnit unit,
                                        String lockKeyPrefix){

        String key = keyPrefix + id;
        //1.从redis查
        String json = stringRedisTemplate.opsForValue().get(key);


        //2.存在返回
        if (StrUtil.isNotBlank(json)){
            R r1 = JSONUtil.toBean(json, type);
            return r1;
        }

        //命中空值
        if ("".equals(json)) {
            return null;
        }

        //缓存重建
        String lockKey = lockKeyPrefix + id;
        R r2 =null;
        try {
            boolean isLock = tryLock(lockKey);

            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id, type,
                        dbFallback,
                        time ,
                     unit,
                   lockKeyPrefix);
            }

            //3.不存在查数据库
            r2 = dbFallback.apply(id);
            Thread.sleep(200);
            if(r2 ==null){
                //当数据库没有这个时候把空值存进redis  以防每次都查询数据库
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }

        //4.写入redis
        this.set(key,r2,time,unit);

        return r2;
    }


}
