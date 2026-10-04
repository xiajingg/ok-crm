package com.okcrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.NonNull;

import java.time.Duration;

/**
 * 缓存装配。
 *
 * <p>这里做两件事，都是为了「缓存出问题不能把业务带崩」：</p>
 *
 * <h3>1. 兜底的缓存异常处理器</h3>
 * <p>Spring 默认的 {@code SimpleCacheErrorHandler} 会把缓存异常直接抛出去。
 * 后果是：Redis 一有风吹草动，登录、查客户这些主流程全部 500 ——
 * 明明是「有缓存更好、没缓存也能跑」的东西，却变成了硬依赖。</p>
 *
 * <p>本项目的策略是<b>缓存尽力而为</b>：读/写失败只记日志、当作缓存未命中继续走数据库。
 * 只有「失效（evict）」失败会记 ERROR —— 因为那意味着可能读到旧数据。
 * 为了给这种情况兜底，下面所有缓存都设了 TTL，保证陈旧数据会自己过期。</p>
 *
 * <h3>2. 显式配置 RedisCacheManager</h3>
 * <p>Spring Boot 自动配置的 Redis 缓存<b>默认没有 TTL</b>（永不过期）。
 * 对权限缓存来说这很危险：一旦某次失效失败，旧权限可能一直生效。
 * 所以这里显式设置 TTL 与 key 前缀。</p>
 */
@Slf4j
@Configuration
public class CacheConfig implements CachingConfigurer {

    /** 缓存默认存活时间。故意设得比较短：权限类数据宁可多查几次库，也不能长期陈旧 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** Redis 中的 key 前缀，避免与同一实例上其它项目的缓存撞名 */
    private static final String KEY_PREFIX = "okcrm:cache:";

    @Override
    @NonNull
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(@NonNull RuntimeException exception, @NonNull Cache cache,
                                            @NonNull Object key) {
                // 当作未命中：方法会照常执行并查库，结果正确，只是没走缓存
                log.warn("缓存读取失败，已降级为直接查库。cache={}, key={}, 原因={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(@NonNull RuntimeException exception, @NonNull Cache cache,
                                            @NonNull Object key, Object value) {
                log.warn("缓存写入失败，本次结果未缓存。cache={}, key={}, 原因={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(@NonNull RuntimeException exception, @NonNull Cache cache,
                                              @NonNull Object key) {
                // 失效失败意味着可能读到旧数据，必须显式告警；靠 TTL 兜底自动过期
                log.error("缓存失效失败！可能短暂读到旧数据，将在 TTL({} 分钟) 后自动过期。"
                                + "cache={}, key={}, 原因={}",
                        DEFAULT_TTL.toMinutes(), cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException exception, @NonNull Cache cache) {
                log.error("缓存清空失败！可能短暂读到旧数据，将在 TTL({} 分钟) 后自动过期。cache={}, 原因={}",
                        DEFAULT_TTL.toMinutes(), cache.getName(), exception.getMessage());
            }
        };
    }

    /**
     * Redis 缓存管理器（仅当 {@code spring.cache.type=redis} 时生效）。
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .prefixCacheNameWith(KEY_PREFIX)
                // key 用字符串（RedisCache 内部会经 ConversionService 把 Long 等转成 String）
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.string()))
                // 值用 JDK 序列化，不用 JSON —— 这里踩过坑：
                // GenericJackson2JsonRedisSerializer 开启默认类型信息后，
                // 对 Set<String>（实际类型是 JDK 内部的 ImmutableCollections$SetN）
                // 是「写得进、读不出」：反序列化时报
                //   Could not resolve type id 'xxx' as a subtype of java.lang.Object
                // 结果是缓存永远命中不了，还会被下面的兜底处理器静默降级成每次都查库。
                // JDK 序列化对任意 Serializable 集合类型都能正确往返，代价只是值不可读。
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.java()))
                .disableCachingNullValues();

        log.info("缓存实现：Redis（TTL={} 分钟，key 前缀 {}）", DEFAULT_TTL.toMinutes(), KEY_PREFIX);
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }
}
