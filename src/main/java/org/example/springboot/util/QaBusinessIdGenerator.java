package org.example.springboot.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户端问答业务编号生成工具。
 */
public final class QaBusinessIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private QaBusinessIdGenerator() {
    }

    public static String nextSessionNo() {
        return "SES_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public static String nextMessageNo() {
        return "MSG_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public static String nextRequestNo() {
        return "REQ_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public static String nextExceptionNo() {
        return "EX_" + FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
