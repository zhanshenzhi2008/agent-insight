package com.llm.insight.common;

/**
 * 极简雪花 ID（项目内元数据表数据量小，单机够用）。
 * <p>
 * 41 位时间戳(毫秒) + 12 位序列 + 10 位机器/进程标识。
 */
public final class SnowflakeIdGenerator {

    private static final long EPOCH = 1_700_000_000_000L; // 2023-11 起点
    private static final long NODE_BITS = 10L;
    private static final long SEQ_BITS = 12L;

    private SnowflakeIdGenerator() {}

    public static synchronized long next() {
        long ts = System.currentTimeMillis() - EPOCH;
        long seq = System.nanoTime() & ((1L << SEQ_BITS) - 1);
        long node = Runtime.getRuntime().hashCode() & ((1L << NODE_BITS) - 1);
        return (ts << (NODE_BITS + SEQ_BITS)) | (node << SEQ_BITS) | seq;
    }
}