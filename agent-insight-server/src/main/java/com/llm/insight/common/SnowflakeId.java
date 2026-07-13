package com.llm.insight.common;

import java.time.Instant;

/**
 * 朴素雪花 ID 生成器（63 bit，仿 Twitter Snowflake 结构）。
 * <p>
 * 结构（从高到低）：
 * <pre>
 *   1 bit  sign      = 0
 *   41 bit timestamp = (now - EPOCH) in ms
 *   10 bit machineId = 0  (固定，单实例部署足够；未来分布式可改为读取 hostname hash)
 *  11 bit sequence   = 同 ms 内自增
 * </pre>
 *
 * <p>足够支撑单实例 agent-insight 服务数百年；无需第三方依赖。
 * <p><b>注意</b>：仅保证**单 JVM 进程内**单调；多实例场景需引入 workerId 分配（etcd/zookeeper），
 * 当前架构不涉及。
 */
public final class SnowflakeId {

    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long TIMESTAMP_BITS = 41L;
    private static final long MACHINE_BITS = 10L;
    private static final long SEQUENCE_BITS = 11L;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_ID = 0L; // 单实例固定为 0
    private static final long MACHINE_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS;

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private SnowflakeId() {}

    public static synchronized long next() {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 同 ms 内序列用完，等待下一毫秒
                while (now <= lastTimestamp) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;

        long elapsed = now - EPOCH;
        if (elapsed < 0 || elapsed >= (1L << TIMESTAMP_BITS)) {
            throw new IllegalStateException("Snowflake 时间戳溢出，请检查系统时钟或 EPOCH 配置");
        }
        return (elapsed << TIMESTAMP_SHIFT)
                | (MACHINE_ID << MACHINE_SHIFT)
                | sequence;
    }
}