package org.example.springboot.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 图谱业务 ID 生成工具，用于实体和关系的前缀化编号生成。
 */
public final class GraphIdGenerator {

    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter VERSION_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private GraphIdGenerator() {
    }

    public static String nextEntityId() {
        return nextId("ENT");
    }

    public static String nextRelationId() {
        return nextId("REL");
    }

    public static String nextVersionNo() {
        return "v" + LocalDateTime.now().format(VERSION_TIME_FORMATTER);
    }

    private static String nextId(String prefix) {
        // 批量导入会在同一秒内创建大量节点，毫秒时间戳叠加单调序列可避免随机数碰撞。
        long sequence = SEQUENCE.updateAndGet(value -> value >= 999_999 ? 1 : value + 1);
        return prefix + "_" + ID_TIME_FORMATTER.format(LocalDateTime.now()) + String.format("%06d", sequence);
    }
}
