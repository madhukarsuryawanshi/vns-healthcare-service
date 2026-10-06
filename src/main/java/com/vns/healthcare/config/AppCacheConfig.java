package com.vns.healthcare.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class AppCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "employee-lists",
                "employee-pages",
                "employee-active",
                "employee-active-pages",
                "employeeById",
                "customer-lists",
                "customer-pages",
                "customerById",
                "dashboard-stats",
                "attendance-monthly",
                "attendance-summary",
                "salary-register"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(20, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
