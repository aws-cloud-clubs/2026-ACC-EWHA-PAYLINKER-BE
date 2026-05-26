package com.paylinker.worker.util;

import java.util.UUID;

public final class IdUtil {

    private IdUtil() {}

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
