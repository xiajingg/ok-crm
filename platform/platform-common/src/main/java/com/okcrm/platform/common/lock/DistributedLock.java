package com.okcrm.platform.common.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 分布式锁抽象。
 *
 * <p>存在的理由：公海池回收这类定时任务在多实例部署时必须互斥，
 * 否则两个节点会同时对同一个客户执行回收，产生重复日志甚至数据竞争。</p>
 *
 * <p>实现按环境切换（与缓存策略同一套思路）：</p>
 * <ul>
 *   <li>单机 / 开发 / 测试 → {@link InMemoryDistributedLock}（进程内锁）</li>
 *   <li>多实例生产 → Redis 实现（SET NX EX）</li>
 * </ul>
 * <p>业务代码只依赖本接口，不感知底层。</p>
 */
public interface DistributedLock {

    /**
     * 尝试加锁，不阻塞。
     *
     * @param key 锁标识
     * @param ttl 锁的自动过期时间；必须设置，否则持锁进程崩溃会永久死锁
     * @return true 表示加锁成功
     */
    boolean tryLock(String key, Duration ttl);

    /**
     * 释放锁。只有持锁者才应该调用。
     */
    void unlock(String key);

    /**
     * 拿到锁才执行，拿不到返回 {@link Optional#empty()}。
     */
    default <T> Optional<T> runIfLocked(String key, Duration ttl, Supplier<T> action) {
        if (!tryLock(key, ttl)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(action.get());
        } finally {
            unlock(key);
        }
    }
}
