package com.example.idempotent.idempotent.common.config;

import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


/**
 * @author: Gavin
 * @see: redisson config
 */
@Component
public class redissonConfig {

    @Bean
    public Redisson redisson() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setDatabase(0);
        return (Redisson) Redisson.create(config);
    }
}
