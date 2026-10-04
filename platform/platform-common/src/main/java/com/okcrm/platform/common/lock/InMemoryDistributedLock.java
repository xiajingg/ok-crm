package com.okcrm.platform.common.lock;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内分布式锁（单实例部署与本地开发用）。
 *
 * <p><b>它不是真正的分布式锁</b>：只在本 JVM 内互斥。多实例部署时必须换成 Redis 实现，
 * 否则各节点会各自跑一遍回收任务。名字保留「Distributed」是为了让替换点显式可见。</p>
 */
@Slf4j
public class InMemoryDistributedLock implements DistributedLock {

    private final Map<String, Long> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long expireAt = now + ttl.toMillis();

        Long existing = locks.putIfAbsent(key, expireAt);
        if (existing == null) {
            return true;
        }
        // 已过期则抢占
        if (existing <= now) {
            return locks.replace(key, existing, expireAt);
        }
        return false;
    }

    @Override
    public void unlock(String key) {
        locks.remove(key);
    }
}
