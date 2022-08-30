package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisUtils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.SimpleRedisLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    public RedissonClient redissonClient;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始");
        }
        //3.查询秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束");
        }
        //4.查询库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        UserDTO user = UserHolder.getUser();
        Long id = user.getId();

        //SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);

        boolean isLock = lock.tryLock();

        if(!isLock){
            return Result.fail("不允许重复下单");
        }

//        synchronized (id.toString().intern() ) {
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.oneByOne(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

//        }
    }

    @Transactional
    public Result oneByOne(Long voucherId) {
        //一人一单
        UserDTO user = UserHolder.getUser();
        Long id = user.getId();



            int count = query().eq("user_id", id)
                    .eq("voucher_id", voucherId).count();
            if (count > 0) {

                return Result.fail("该用户已经购买");
            }

            //5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1 ")
                    .eq("voucher_id", voucherId).gt("stock", 0).update();
            //6.创建订单
            if (!success) {
                return Result.fail("库存不足");
            }
            //7.返回订单ID
            VoucherOrder voucherOrder = new VoucherOrder();
            //1.订单ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            //1.用户ID

            voucherOrder.setUserId(id);
            //1.代金券ID
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);
        }

}
