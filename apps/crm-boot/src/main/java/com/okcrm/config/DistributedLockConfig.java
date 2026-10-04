package com.okcrm.config;

import com.okcrm.platform.common.lock.DistributedLock;
import com.okcrm.platform.common.lock.InMemoryDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 分布式锁装配。
 *
 * <p>与缓存策略保持同一套切换逻辑：单机开发/测试用进程内锁，
 * 生产（{@code spring.cache.type=redis}）自动换成 Redis 锁。
 * 业务代码只依赖 {@link DistributedLock} 接口，不需要改任何一行。</p>
 */
@Slf4j
@Configuration
public class DistributedLockConfig {

    @Bean
    public DistributedLock distributedLock(Environment environment,
                                           ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        String cacheType = environment.getProperty("spring.cache.type", "simple");
        if ("redis".equalsIgnoreCase(cacheType)) {
            StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
            if (template != null) {
                log.info("分布式锁实现：Redis");
                return new RedisDistributedLock(template);
            }
            log.warn("配置了 spring.cache.type=redis 但 StringRedisTemplate 不可用，回退为进程内锁");
        }
        log.info("分布式锁实现：进程内（仅适用于单实例部署）");
        return new InMemoryDistributedLock();
    }

    /**
     * 基于 Redis SET NX EX 的分布式锁。
     *
     * <p>释放时用 Lua 脚本校验持有者令牌，避免「A 的锁过期后 B 拿到锁，
     * 随后 A 执行完把自己的 unlock 打到 B 的锁上」这类误删。</p>
     */
    static class RedisDistributedLock implements DistributedLock {

        private static final String KEY_PREFIX = "okcrm:lock:";

        private static final String UNLOCK_SCRIPT = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        private final StringRedisTemplate redisTemplate;
        private final ConcurrentMap<String, String> localTokens = new ConcurrentHashMap<>();

        RedisDistributedLock(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public boolean tryLock(String key, Duration ttl) {
            String redisKey = KEY_PREFIX + key;
            String token = UUID.randomUUID().toString();

            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                localTokens.put(key, token);
                return true;
            }
            return false;
        }

        @Override
        public void unlock(String key) {
            String token = localTokens.remove(key);
            if (token == null) {
                return;
            }
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            redisTemplate.execute(script, List.of(KEY_PREFIX + key), token);
        }
    }
}
