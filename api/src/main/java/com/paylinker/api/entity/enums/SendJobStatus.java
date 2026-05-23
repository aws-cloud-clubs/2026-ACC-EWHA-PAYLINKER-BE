package com.paylinker.api.entity.enums;

public enum SendJobStatus {
    REQUESTED,
    QUEUED,
    SENDING,
    SUCCESS,
    FAILED,
    RETRYING,
    CANCELLED
}