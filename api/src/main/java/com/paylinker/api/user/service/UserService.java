package com.paylinker.api.user.service;

import com.paylinker.api.user.dto.response.UserMeResponse;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public UserMeResponse getMyProfile(Authentication authentication) {

        // 1. adminId 추출
        String adminId = authentication.getName();
        if (adminId == null || adminId.isBlank()) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 2. JWT 객체 추출하여 클레임(Claim) 확인
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 3. 클레임에서 필수 정보 추출
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        // 필수 값 누락 시 예외 발생
        if (email == null || email.isBlank() || name == null || name.isBlank()) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 4. createdAt 처리 로직
        // Cognito Custom Attribute(custom:createdAt)를 사용하며,
        // 값이 없을 경우 에러를 던져 의도되지 않은 동작을 차단
        String createdAt = jwt.getClaimAsString("custom:createdAt");

        if (createdAt == null || createdAt.isBlank()) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        return new UserMeResponse(
                adminId,
                email,
                name,
                "ADMIN", // 명세서 기준 고정값
                createdAt
        );
    }
}