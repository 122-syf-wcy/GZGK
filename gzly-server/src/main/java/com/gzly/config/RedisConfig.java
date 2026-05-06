package com.gzly.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisConfig implements CachingConfigurer {

    ObjectMapper redisObjectMapper() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.gzly.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build();
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);
        return om;
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        return new GenericJackson2JsonRedisSerializer(redisObjectMapper());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        GenericJackson2JsonRedisSerializer serializer = jsonSerializer();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = jsonSerializer();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> "gzly:v7:" + cacheName + "::")
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration("universities", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("universityDetail", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("universityBySchoolId", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("scoreLines", config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("years", config.entryTtl(Duration.ofHours(48)))
                .withCacheConfiguration("candidateScoreLines", config.entryTtl(Duration.ofMinutes(20)))
                .withCacheConfiguration("candidateMajorScores", config.entryTtl(Duration.ofMinutes(20)))
                .withCacheConfiguration("admissionProbabilities", config.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration("riskAssessments", config.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration("scorePredictions", config.entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration("rankEstimates", config.entryTtl(Duration.ofHours(12)))
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("读取缓存失败，已回源查询: cache={}, key={}", cache.getName(), key, exception);
                evictBrokenCache(cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("写入缓存失败，已忽略: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("删除缓存失败，已忽略: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("清空缓存失败，已忽略: cache={}", cache.getName(), exception);
            }

            private void evictBrokenCache(Cache cache, Object key) {
                try {
                    cache.evictIfPresent(key);
                } catch (RuntimeException e) {
                    log.warn("清理异常缓存失败: cache={}, key={}", cache.getName(), key, e);
                }
            }
        };
    }
}
