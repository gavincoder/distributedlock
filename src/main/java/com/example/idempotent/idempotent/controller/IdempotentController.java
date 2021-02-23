package com.example.idempotent.idempotent.controller;

import com.example.idempotent.idempotent.annotation.PreventDuplication;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/web")
public class IdempotentController {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private Redisson redisson;

    @PostMapping("/sayNoDuplication")
    @PreventDuplication(expireSeconds = 15)
    public String sayNoDuplication(@RequestParam("requestNum") String requestNum) throws InterruptedException {
        log.info("sayNoDuplicatin requestNum：{}", requestNum);
        Thread.sleep(3000);// 延时0.5s ，确保当前线程执行方法完成要晚于后续线程到达
        return "sayNoDuplicatin".concat(requestNum);
    }

    @GetMapping("/initStockNum")
    public Object initStockNum(String[] args) {
        redisTemplate.opsForValue().set("stockNum", "1000");
        Object name = redisTemplate.opsForValue().get("stockNum");
        return name;
    }

    @PostMapping("/buyProduct1")
    public String buyProduct1() {
        String buyerName = "顾客" + Thread.currentThread().getId();
        Object stObj = redisTemplate.opsForValue().get("stockNum");
        int stockNum = Integer.parseInt(stObj.toString());
        if (stockNum > 0) {
            redisTemplate.opsForValue().set("stockNum", --stockNum);
            System.out.println(buyerName + "下单成功，库存剩余件数：" + stockNum);
        } else {
            System.out.println(buyerName + "下单失败，库存不足.");
            return buyerName + "下单失败！";
        }
        return buyerName + "下单成功!";
    }

    @PostMapping("/buyProduct2")
    public String buyProduct2() {
        String buyerName = "顾客" + Thread.currentThread().getId();
        String lockKey = "buyProductLock";
        String lockValue = UUID.randomUUID().toString().concat(UUID.randomUUID().toString());
        try {
            // setIfAbsent是java中的方法，setnx是redis命令中的方法
            // 1.保证系统崩溃可以自然释放锁
            // 2.保证redis操作原子性，避免设置超时时刻系统崩溃
            Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
            if (!isSuccess) {
                System.out.println("系统繁忙,请稍后重试.");
                return "系统繁忙,请稍后重试.";
            }
            int stockNum = Integer.parseInt(redisTemplate.opsForValue().get("stockNum").toString());
            if (stockNum > 0) {
                redisTemplate.opsForValue().set("stockNum", --stockNum);
                System.out.println(buyerName + "下单成功，库存剩余件数：" + stockNum);
            } else {
                System.out.println(buyerName + "下单失败，库存不足.");
                return buyerName + "下单失败!";
            }
        } finally {//3.保证操作成功和系统异常情况下都能释放锁
            //4.采用线程标识主动检查，保证仅删除自己的锁。避免redis超时时间小于业务逻辑执行时间，前一个线程释放了后一个线程的加锁，造成锁永久失效。
            //lockValue存储方法栈中线程私有
            if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
                //释放锁
                redisTemplate.delete(lockKey);
            }
        }
        return buyerName + "下单成功!";
    }

    @PostMapping("/buyProduct3")
    public String buyProduct3() {
        String buyerName = "顾客" + Thread.currentThread().getId();
        String lockKey = "buyProductLock";
        // redisson加锁
        RLock redissonLock = redisson.getLock(lockKey);
        try {
            //redisson设置锁时间
            redissonLock.lock(10, TimeUnit.SECONDS);
            int stockNum = Integer.parseInt(redisTemplate.opsForValue().get("stockNum").toString());
            if (stockNum > 0) {
                redisTemplate.opsForValue().set("stockNum", --stockNum);
                System.out.println(buyerName + "下单成功，库存剩余件数：" + stockNum);
            } else {
                System.out.println(buyerName + "下单失败，库存不足.");
                return buyerName + "下单失败!";
            }
        } finally {
            //redisson释放锁
            redissonLock.unlock();
        }
        return buyerName + "下单成功!";
    }

}
