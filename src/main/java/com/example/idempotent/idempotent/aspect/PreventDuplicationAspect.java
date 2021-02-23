package com.example.idempotent.idempotent.aspect;

import com.alibaba.fastjson.JSONObject;
import com.example.idempotent.idempotent.annotation.PreventDuplication;
import com.example.idempotent.idempotent.common.constant.IdempotentConstant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author :Gavin
 * @see :防止重复操作切面（处理切面注解）
 */

@Aspect
@Component
public class PreventDuplicationAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 定义切点
     */
    @Pointcut("@annotation(com.example.idempotent.idempotent.annotation.PreventDuplication)")
    public void preventDuplication() {
    }

    /**
     * 环绕通知 （可以控制目标方法前中后期执行操作）
     *
     * @param joinPoint
     * @return
     */
    @Around("preventDuplication()")
    public Object before(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        Assert.notNull(request, "request cannot be null.");
        //获取执行方法
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        //获取防重复提交注解
        PreventDuplication annotation = method.getAnnotation(PreventDuplication.class);
        // 获取token以及方法标记，生成redisKey和redisValue
        String token = request.getHeader(IdempotentConstant.TOKEN);
        String redisKey = IdempotentConstant.PREVENT_DUPLICATION_PREFIX
                .concat(token)
                .concat(getMethodSign(method, joinPoint.getArgs()));
        String redisValue = redisKey.concat(annotation.value()).concat("submit duplication");
        System.out.print("当前线程号：" + Thread.currentThread().getId());
        System.out.println("存储redisKey: " + redisKey);
        redisValue.concat(UUID.randomUUID().toString() + Thread.currentThread().getId());
        try {
            //设置防重复操作限时标记（前置通知）
            //redisTemplate实现jedis.setnx(key,value)
            //setIfAbsent 是java中的方法
            //setnx 是 redis命令中的方法
            Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(redisKey, redisValue, annotation.expireSeconds(), TimeUnit.SECONDS);
            System.out.println("当前线程号：" + Thread.currentThread().getId() + "," + "startTime:" + isSuccess);
            long startTime = System.currentTimeMillis();
            if (!isSuccess) {
                throw new RuntimeException("请勿重复提交");
            }
            System.out.println("当前线程号：" + Thread.currentThread().getId() + "," + "startTime:" + startTime + "ms耗时");

            //ProceedingJoinPoint类型参数可以决定是否执行目标方法，且环绕通知必须要有返回值，返回值即为目标方法的返回值
            Object proceed = joinPoint.proceed();
            long endStart = System.currentTimeMillis();
            System.out.println("当前线程号：" + Thread.currentThread().getId() + "," + "endStart:" + endStart + "ms耗时");
            return proceed;
        } finally {
            //释放锁校验是否为当前线程
            if (redisValue.equals(redisTemplate.opsForValue().get(redisKey))) {
                //释放锁
                redisTemplate.delete(redisKey);
            }
        }
    }

    /**
     * 生成方法标记：采用数字签名算法SHA1对方法签名字符串加签
     *
     * @param method
     * @param args
     * @return
     */
    private String getMethodSign(Method method, Object... args) {
        StringBuilder sb = new StringBuilder(method.toString());
        for (Object arg : args) {
            sb.append(toString(arg));
        }
        return DigestUtils.sha1DigestAsHex(sb.toString());
    }

    private String toString(Object arg) {
        if (Objects.isNull(arg)) {
            return "null";
        }
        if (arg instanceof Number) {
            return arg.toString();
        }
        return JSONObject.toJSONString(arg);
    }

}
