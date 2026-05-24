package com.paylinker.api.user.dto.response;

public record UserMeResponse(
        String adminId,
        String email,
        String name,
        String role,
        String createdAt
) {}