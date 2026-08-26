package com.api.fespot.global.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableCaching //@Cacheable, @CacheEvict 활성화
public class CaffeineConfig {

    @Bean
    public CacheManager cacheManager() {
        List<CaffeineCache> caffeineCaches = Arrays.stream(CaffeineCacheDefinition.values())
                .map(this::caffeineCache)
                .toList();

        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(caffeineCaches);
        return simpleCacheManager;
    }

    private CaffeineCache caffeineCache(CaffeineCacheDefinition cacheType) {
        return new CaffeineCache(
                cacheType.getCacheName(),
                Caffeine.newBuilder()
                        .recordStats()
                        .expireAfterWrite(cacheType.getExpireAfterWrite())
                        .maximumSize(cacheType.getMaximumSize())
                        .build()
        );
    }
}
