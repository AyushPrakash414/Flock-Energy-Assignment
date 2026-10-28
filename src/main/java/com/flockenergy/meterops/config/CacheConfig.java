package com.flockenergy.meterops.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-memory cache configuration.
 * The "meters-export" cache holds the full 403-meter dataset fetched
 * via the HMAC-signed export endpoint, enabling sub-millisecond
 * pagination, search, and individual meter lookups.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(AppProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager("meters-export", "transformers");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(props.getCacheTtlMinutes(), TimeUnit.MINUTES)
                        .maximumSize(5)
                        .recordStats()
        );
        return manager;
    }
}
