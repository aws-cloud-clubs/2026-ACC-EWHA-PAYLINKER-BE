package com.paylinker.worker.util;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class Attr {

    private Attr() {}

    public static AttributeValue s(String v) {
        return AttributeValue.fromS(v == null ? "" : v);
    }

    public static AttributeValue n(long v) {
        return AttributeValue.fromN(String.valueOf(v));
    }

    public static AttributeValue n(int v) {
        return AttributeValue.fromN(String.valueOf(v));
    }

    public static AttributeValue b(boolean v) {
        return AttributeValue.fromBool(v);
    }

    public static String str(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        if (v == null) return null;
        return v.s();
    }

    public static Integer num(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        if (v == null || v.n() == null) return null;
        try {
            return Integer.parseInt(v.n());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
