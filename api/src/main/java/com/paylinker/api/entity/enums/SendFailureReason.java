package com.paylinker.api.entity.enums;

public enum SendFailureReason {
    INVALID_EMAIL,
    BLOCKED,
    BOUNCED,
    COMPLAINT,
    TEMPORARY_FAILURE,
    SYSTEM_ERROR,
    RATE_LIMITED,
    UNKNOWN,
    UNSUBSCRIBED
}