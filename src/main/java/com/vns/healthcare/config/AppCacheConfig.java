package com.vns.healthcare.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class AppCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "employee-lists",
                "employee-pages",
                "employee-active",
                "employee-active-pages",
                "employeeById",
                "customer-lists",
                "customer-pages",
                "customerById"
        );
        return cacheManager;
    }
}
