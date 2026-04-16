package org.example.springboot.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class GraphIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private GraphIdGenerator() {
    }

    public static String nextEntityId() {
        return "ENT_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public static String nextRelationId() {
        return "REL_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public static String nextVersionNo() {
        return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmmss"));
    }
}
